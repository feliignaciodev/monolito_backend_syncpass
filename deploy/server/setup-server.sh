#!/bin/bash
# Prepara una instancia Amazon Linux 2023 para SyncPass API (paso 5 de pasos.md).
# Se puede ejecutar de nuevo sin romper nada.
# Uso: sudo bash setup-server.sh [region]
set -euo pipefail

REGION="${1:-us-east-1}"
DIR="$(cd "$(dirname "$0")" && pwd)"

if [ "$(id -u)" -ne 0 ]; then
  echo "Ejecute con sudo" >&2
  exit 1
fi

echo "==> Actualizando paquetes e instalando Java 21 y nginx"
dnf -y upgrade --refresh
dnf -y install java-21-amazon-corretto-headless nginx
java -version

echo "==> Usuario de servicio 'syncpass' (sin shell ni privilegios)"
id syncpass &>/dev/null || useradd --system --home /opt/syncpass --shell /sbin/nologin syncpass
install -d -o syncpass -g syncpass -m 750 /opt/syncpass

echo "==> Swap de 1 GB"
if ! swapon --show | grep -q /swapfile; then
  [ -f /swapfile ] || dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
fi
grep -q '^/swapfile' /etc/fstab || echo '/swapfile swap swap defaults 0 0' >> /etc/fstab

echo "==> Script de arranque y servicio systemd"
sed "s/__REGION__/${REGION}/g" "$DIR/start.sh" > /opt/syncpass/start.sh
chown syncpass:syncpass /opt/syncpass/start.sh
chmod 750 /opt/syncpass/start.sh
install -m 644 "$DIR/syncpass.service" /etc/systemd/system/syncpass.service
systemctl daemon-reload
systemctl enable syncpass

echo "==> nginx: solo acepta peticiones que traen el secreto de API Gateway"
# Hexadecimal (lo genera parameter-store.ps1), seguro para sed y para nginx
SECRETO=$(aws ssm get-parameter --name /syncpass/gateway/ORIGIN_SECRET --with-decryption \
  --region "$REGION" --query Parameter.Value --output text)
if ! [[ "$SECRETO" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Falta o es invalido /syncpass/gateway/ORIGIN_SECRET; ejecute deploy/parameter-store.ps1" >&2
  exit 1
fi
install -m 600 /dev/null /etc/nginx/conf.d/syncpass.conf
sed "s/__ORIGIN_SECRET__/${SECRETO}/g" "$DIR/nginx-syncpass.conf" > /etc/nginx/conf.d/syncpass.conf
# El server de ejemplo de nginx.conf no puede quedarse con el default_server
sed -i -E 's/listen(\s+)(\[::\]:)?80(\s+)default_server;/listen\1\280;/' /etc/nginx/nginx.conf
nginx -t
systemctl enable --now nginx
systemctl reload nginx

echo "==> Comprobando Parameter Store con el rol IAM de la instancia"
cantidad=$(aws ssm get-parameters-by-path --path /syncpass/prod --region "$REGION" \
  --query 'length(Parameters)' --output text)
echo "Parametros encontrados en /syncpass/prod: ${cantidad} (se esperan 6)"
if [ "${cantidad}" -lt 6 ]; then
  echo "AVISO: faltan parametros; ver paso 3 de pasos.md o deploy/parameter-store.ps1" >&2
fi

if [ -f /opt/syncpass/app.jar ]; then
  systemctl restart syncpass
  echo "==> Servicio reiniciado con el JAR existente"
else
  echo "==> Listo. Falta subir el JAR (deploy.ps1 lo hace a continuacion)"
fi
