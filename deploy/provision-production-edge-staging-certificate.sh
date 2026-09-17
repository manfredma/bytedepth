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
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly EDGE_CONFIG_SOURCE="$SOURCE_ROOT/deploy/nginx/staging-legacy-production-entry.conf"
readonly EDGE_CONFIG=/opt/nginx-conf.d/staging-legacy-production-entry.conf

if [[ ! -r "$EDGE_CONFIG_SOURCE" ]]; then
    printf 'Missing versioned legacy production-entry config: %s\n' "$EDGE_CONFIG_SOURCE" >&2
    exit 1
fi

certbot certonly \
    --standalone \
    --preferred-challenges http \
    --non-interactive \
    --agree-tos \
    --register-unsafely-without-email \
    --keep-until-expiring \
    --cert-name "$CERT_NAME" \
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

if ! openssl x509 -checkend 2592000 -noout -in "$CERT_DIR/fullchain.pem" >/dev/null; then
    printf 'Refusing: production edge certificate is expired or expires within 30 days.\n' >&2
    exit 1
fi
certificate_public_key="$(openssl x509 -in "$CERT_DIR/fullchain.pem" -pubkey -noout \
    | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
private_key_public_key="$(openssl pkey -in "$CERT_DIR/privkey.pem" -pubout -outform DER 2>/dev/null \
    | sha256sum | awk '{print $1}')"
if [[ -z "$certificate_public_key" || "$certificate_public_key" != "$private_key_public_key" ]]; then
    printf 'Refusing: production edge certificate and private key do not match.\n' >&2
    exit 1
fi

install -o root -g root -m 0644 "$EDGE_CONFIG_SOURCE" "$EDGE_CONFIG"
docker exec "$NGINX_CONTAINER" nginx -t
docker exec "$NGINX_CONTAINER" nginx -s reload
printf 'Provisioned %s certificate on the production edge.\n' "$CERT_NAME"
