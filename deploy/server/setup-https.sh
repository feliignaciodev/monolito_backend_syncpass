#!/bin/bash
# Certificado HTTPS gratuito (Let's Encrypt) para nginx y renovacion automatica (paso 8.3 de pasos.md).
# Requisito: el dominio ya apunta a la Elastic IP y el puerto 80 esta abierto.
# Uso: sudo bash setup-https.sh <dominio> <email-para-avisos>
set -euo pipefail

DOMINIO="${1:?Uso: sudo bash setup-https.sh <dominio> <email>}"
EMAIL="${2:?Uso: sudo bash setup-https.sh <dominio> <email>}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Ejecute con sudo" >&2
  exit 1
fi

echo "==> Instalando certbot (Amazon Linux 2023 no lo trae en sus repositorios)"
dnf -y install python3 augeas-libs cronie
if [ ! -x /opt/certbot/bin/certbot ]; then
  python3 -m venv /opt/certbot
  /opt/certbot/bin/pip install --upgrade pip
  /opt/certbot/bin/pip install certbot certbot-nginx
fi
ln -sf /opt/certbot/bin/certbot /usr/bin/certbot

echo "==> Emitiendo certificado para ${DOMINIO}"
certbot --nginx -d "$DOMINIO" --non-interactive --agree-tos -m "$EMAIL" --redirect

echo "==> Renovacion automatica diaria"
echo "0 3 * * * root /usr/bin/certbot renew -q --deploy-hook 'systemctl reload nginx'" > /etc/cron.d/certbot
systemctl enable --now crond

echo "==> Listo: https://${DOMINIO}/api/health"
