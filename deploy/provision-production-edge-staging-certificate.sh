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
SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT

# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/production-staging-edge.sh"

if [[ ! -r "$SOURCE_ROOT/deploy/nginx/staging-legacy-production-entry.conf" ]]; then
    printf 'Missing versioned legacy production-entry config.\n' >&2
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
    --pre-hook "systemctl stop \"$(if systemctl cat bytedepth-production-green-public-nginx.service >/dev/null 2>&1; then printf '%s' bytedepth-production-green-public-nginx.service; else printf '%s' nginx.service; fi)\" || true" \
    --post-hook "systemctl start \"$(if systemctl cat bytedepth-production-green-public-nginx.service >/dev/null 2>&1; then printf '%s' bytedepth-production-green-public-nginx.service; else printf '%s' nginx.service; fi)\"" \
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

production_install_staging_edge_routes "$SOURCE_ROOT"
printf 'Provisioned %s certificate on the production edge.\n' "$CERT_NAME"
