#!/usr/bin/env bash
# 在 175 上为仍解析到生产边缘的旧 staging 域名签发证书，并保持上一版的
# 生产入口跳转逻辑。certbot 的 standalone challenge 由 DNS 指向 175 完成。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/provision-production-edge-staging-certificate.sh\n' >&2
    exit 1
fi

readonly CERT_NAME=staging.bytedepth.cn
readonly CERT_DIR="/etc/letsencrypt/live/$CERT_NAME"
readonly NGINX_CONTAINER=bytedepth-nginx-1

certbot certonly \
    --standalone \
    --preferred-challenges http \
    --non-interactive \
    --agree-tos \
    --register-unsafely-without-email \
    --keep-until-expiring \
    --pre-hook "docker stop $NGINX_CONTAINER || true" \
    --post-hook "docker start $NGINX_CONTAINER" \
    -d "$CERT_NAME"

san_names="$(openssl x509 -in "$CERT_DIR/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
if ! printf '%s\n' "$san_names" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | grep -Fx "DNS:$CERT_NAME" >/dev/null; then
    printf 'Refusing: production edge certificate does not contain exact SAN DNS:%s\n' "$CERT_NAME" >&2
    exit 1
fi

docker exec "$NGINX_CONTAINER" nginx -t
docker exec "$NGINX_CONTAINER" nginx -s reload
printf 'Provisioned %s certificate on the production edge.\n' "$CERT_NAME"
