#!/bin/bash
# Prepara una instancia Amazon Linux 2023 para SyncPass API (pasos 5, 7 y 8.2 de pasos.md).
# Se puede ejecutar de nuevo sin romper nada.
# Uso: sudo bash setup-server.sh <dominio> [region]
set -euo pipefail

DOMINIO="${1:?Uso: sudo bash setup-server.sh <dominio> [region]}"
REGION="${2:-us-east-1}"
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

# /tmp se borra al reiniciar: el script de HTTPS queda instalado de forma permanente
install -m 755 "$DIR/setup-https.sh" /usr/local/sbin/syncpass-setup-https

echo "==> nginx para ${DOMINIO}"
if [ ! -f /etc/nginx/conf.d/syncpass.conf ] || ! grep -q "listen 443" /etc/nginx/conf.d/syncpass.conf; then
  # Solo se reescribe mientras no exista HTTPS, para no borrar lo que agrega certbot
  sed "s/__DOMINIO__/${DOMINIO}/g" "$DIR/nginx-syncpass.conf" > /etc/nginx/conf.d/syncpass.conf
fi
nginx -t
systemctl enable --now nginx
systemctl reload nginx

echo "==> Comprobando Parameter Store con el rol IAM de la instancia"
cantidad=$(aws ssm get-parameters-by-path --path /syncpass/prod --region "$REGION" \
  --query 'length(Parameters)' --output text)
echo "Parametros encontrados en /syncpass/prod: ${cantidad} (se esperan 6)"
if [ "${cantidad}" -lt 6 ]; then
  echo "AVISO: faltan parametros; ver paso 2 de pasos.md o deploy/parameter-store.ps1" >&2
fi

if [ -f /opt/syncpass/app.jar ]; then
  systemctl restart syncpass
  echo "==> Servicio reiniciado con el JAR existente"
else
  echo "==> Listo. Falta subir el JAR (deploy.ps1 lo hace a continuacion)"
fi
