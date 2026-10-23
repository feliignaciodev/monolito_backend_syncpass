#!/bin/bash
# Arranque de SyncPass API en EC2 (lo ejecuta systemd como usuario "syncpass").
# Lee los secretos de Parameter Store (/syncpass/prod/*) con el rol IAM de la instancia:
# no quedan escritos en disco ni en el repositorio.
set -euo pipefail

export AWS_REGION=__REGION__
export SERVER_ADDRESS=127.0.0.1                  # solo accesible a traves de nginx
export SERVER_FORWARD_HEADERS_STRATEGY=native    # respeta X-Forwarded-* del proxy

# /syncpass/prod/DB_URL -> DB_URL, etc.
while IFS=$'\t' read -r name value; do
  export "${name##*/}=${value}"
done < <(aws ssm get-parameters-by-path --path /syncpass/prod --with-decryption \
          --region "$AWS_REGION" --query 'Parameters[*].[Name,Value]' --output text)

for requerida in DB_URL DB_USER DB_PASSWORD COGNITO_ISSUER COGNITO_CLIENT_ID COGNITO_USER_POOL_ID; do
  if [ -z "${!requerida:-}" ]; then
    echo "Falta el parametro /syncpass/prod/${requerida} en Parameter Store" >&2
    exit 1
  fi
done

exec java -Xms256m -Xmx512m -jar /opt/syncpass/app.jar
