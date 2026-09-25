#!/usr/bin/env bash
# 在 124 上签发/更新 staging 域名证书，并安装续期后的 Nginx reload hook。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/provision-staging-certificate.sh\n' >&2
    exit 1
fi

readonly CERT_NAME=staging-bytedepth.bytedepth.cn
readonly CERT_DIR="/etc/letsencrypt/live/$CERT_NAME"
SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly DEPLOY_HOOK_SOURCE="$SOURCE_ROOT/deploy/nginx/reload-nginx-deploy-hook.sh"
readonly DEPLOY_HOOK=/etc/letsencrypt/renewal-hooks/deploy/reload-bytedepth-nginx.sh

readonly CERTBOT_PRE_HOOK='systemctl stop nginx.service || true'
readonly CERTBOT_POST_HOOK='systemctl start nginx.service'

if [[ ! -r "$DEPLOY_HOOK_SOURCE" ]]; then
    printf 'Missing versioned Certbot deploy hook: %s\n' "$DEPLOY_HOOK_SOURCE" >&2
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
    --pre-hook "$CERTBOT_PRE_HOOK" \
    --post-hook "$CERTBOT_POST_HOOK" \
    -d "$CERT_NAME"

install -d -o ubuntu -g ubuntu -m 0755 "$(dirname "$DEPLOY_HOOK")"
install -o ubuntu -g ubuntu -m 0755 "$DEPLOY_HOOK_SOURCE" "$DEPLOY_HOOK"

san_names="$(openssl x509 -in "$CERT_DIR/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
if ! printf '%s\n' "$san_names" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | grep -Fx "DNS:$CERT_NAME" >/dev/null; then
    printf 'Refusing: staging certificate does not contain exact SAN DNS:%s\n' "$CERT_NAME" >&2
    exit 1
fi

if ! openssl x509 -checkend 2592000 -noout -in "$CERT_DIR/fullchain.pem" >/dev/null; then
    printf 'Refusing: staging certificate is expired or expires within 30 days.\n' >&2
    exit 1
fi
certificate_public_key="$(openssl x509 -in "$CERT_DIR/fullchain.pem" -pubkey -noout \
    | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
private_key_public_key="$(openssl pkey -in "$CERT_DIR/privkey.pem" -pubout -outform DER 2>/dev/null \
    | sha256sum | awk '{print $1}')"
if [[ -z "$certificate_public_key" || "$certificate_public_key" != "$private_key_public_key" ]]; then
    printf 'Refusing: staging certificate and private key do not match.\n' >&2
    exit 1
fi

nginx -t
systemctl reload nginx.service
printf 'Provisioned %s certificate and renewal hook.\n' "$CERT_NAME"
