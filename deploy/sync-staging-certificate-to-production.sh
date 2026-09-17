#!/usr/bin/env bash
# 在 175 上执行：从 124 拉取 staging 域名证书，只在生产边缘提供 TLS 握手，
# 不代理 staging 内容。用于证书监控同时探测两台机器的场景。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/sync-staging-certificate-to-production.sh\n' >&2
    exit 1
fi

readonly SYNC_CONF=/etc/bytedepth-sync.conf
readonly CERT_NAME=staging-bytedepth.bytedepth.cn
readonly CERT_DIR="/etc/letsencrypt/live/$CERT_NAME"
readonly LEGACY_CERT_NAME=staging.bytedepth.cn
readonly LEGACY_CERT_DIR="/etc/letsencrypt/live/$LEGACY_CERT_NAME"
readonly EDGE_CONFIG=/opt/nginx-conf.d/staging-bytedepth.conf

if [[ ! -r "$SYNC_CONF" ]]; then
    printf 'Missing %s.\n' "$SYNC_CONF" >&2
    exit 1
fi
# shellcheck disable=SC1090
. "$SYNC_CONF"
readonly STAGING_USER=ubuntu@${STAGING_IP:?STAGING_IP must be set in $SYNC_CONF}
readonly SSH_KEY=${SYNC_SSH_KEY:?SYNC_SSH_KEY must be set in $SYNC_CONF}
readonly SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new)
readonly TEMP_DIR="$(mktemp -d /tmp/bytedepth-staging-cert.XXXXXX)"
trap 'rm -rf "$TEMP_DIR"' EXIT

if [[ ! -r "$LEGACY_CERT_DIR/fullchain.pem" || ! -r "$LEGACY_CERT_DIR/privkey.pem" ]]; then
    printf 'Missing legacy certificate %s; run deploy/provision-production-edge-staging-certificate.sh first.\n' \
        "$LEGACY_CERT_NAME" >&2
    exit 1
fi

install -d -o root -g root -m 0700 "$TEMP_DIR"

staging_exec() {
    ssh "${SSH_OPTS[@]}" "$STAGING_USER" "$1"
}

# Transfer only the two files Nginx needs; never print their contents.
staging_exec "sudo cat /etc/letsencrypt/live/$CERT_NAME/fullchain.pem" > "$TEMP_DIR/fullchain.pem"
staging_exec "sudo cat /etc/letsencrypt/live/$CERT_NAME/privkey.pem" > "$TEMP_DIR/privkey.pem"

san_names="$(openssl x509 -in "$TEMP_DIR/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
if ! printf '%s\n' "$san_names" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | grep -Fx "DNS:$CERT_NAME" >/dev/null; then
    printf 'Refusing: staging certificate does not contain exact SAN DNS:%s\n' "$CERT_NAME" >&2
    exit 1
fi

install -d -o root -g root -m 0700 "$CERT_DIR"
install -o root -g root -m 0644 "$TEMP_DIR/fullchain.pem" "$CERT_DIR/fullchain.pem"
install -o root -g root -m 0600 "$TEMP_DIR/privkey.pem" "$CERT_DIR/privkey.pem"

# Keep the production edge certificate-only route versioned and non-proxying.
install -o root -g root -m 0644 "$(dirname "$0")/nginx/staging-edge-certificate.conf" "$EDGE_CONFIG"
docker exec bytedepth-nginx-1 nginx -t
docker exec bytedepth-nginx-1 nginx -s reload
printf 'Synchronized %s certificate to the production edge.\n' "$CERT_NAME"
