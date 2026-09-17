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
readonly LEGACY_EDGE_CONFIG=/opt/nginx-conf.d/staging-legacy-production-entry.conf
readonly SSH_KNOWN_HOSTS=/root/.ssh/known_hosts

if [[ ! -r "$SYNC_CONF" ]]; then
    printf 'Missing %s.\n' "$SYNC_CONF" >&2
    exit 1
fi
if [[ ! -f "$SYNC_CONF" || "$(stat -c '%U:%G:%a' "$SYNC_CONF")" != 'root:root:600' ]]; then
    printf 'Refusing: %s must be a root-owned regular file with mode 0600.\n' "$SYNC_CONF" >&2
    exit 1
fi
# shellcheck disable=SC1090
. "$SYNC_CONF"
readonly STAGING_USER=ubuntu@${STAGING_IP:?STAGING_IP must be set in $SYNC_CONF}
readonly SSH_KEY=${SYNC_SSH_KEY:?SYNC_SSH_KEY must be set in $SYNC_CONF}
if [[ ! -f "$SSH_KEY" || "$(stat -L -c '%a' "$SSH_KEY")" != '600' ]]; then
    printf 'Refusing: SYNC_SSH_KEY must be a regular file with mode 0600.\n' >&2
    exit 1
fi
if [[ ! -r "$SSH_KNOWN_HOSTS" ]]; then
    printf 'Refusing: SSH known_hosts file is missing or unreadable: %s\n' "$SSH_KNOWN_HOSTS" >&2
    exit 1
fi
readonly SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes \
    -o UserKnownHostsFile="$SSH_KNOWN_HOSTS" -o StrictHostKeyChecking=yes)
readonly TEMP_DIR="$(mktemp -d /tmp/bytedepth-staging-cert.XXXXXX)"
trap 'rm -rf "$TEMP_DIR"' EXIT

if [[ ! -r "$LEGACY_CERT_DIR/fullchain.pem" || ! -r "$LEGACY_CERT_DIR/privkey.pem" ]]; then
    printf 'Missing legacy certificate %s; run deploy/provision-production-edge-staging-certificate.sh first.\n' \
        "$LEGACY_CERT_NAME" >&2
    exit 1
fi

install -d -o root -g root -m 0700 "$TEMP_DIR"
install -d -o root -g root -m 0700 "$TEMP_DIR/legacy" "$TEMP_DIR/staging"

staging_exec() {
    ssh "${SSH_OPTS[@]}" "$STAGING_USER" "$1"
}

validate_certificate_pair() {
    local certificate_label="$1"
    local certificate_file="$2"
    local private_key_file="$3"
    local working_dir="$4"
    local leaf_file="$working_dir/leaf.pem"
    local chain_file="$working_dir/chain.pem"
    local certificate_public_key private_key_public_key

    if ! openssl x509 -checkend 2592000 -noout -in "$certificate_file" >/dev/null; then
        printf 'Refusing: %s certificate is expired or expires within 30 days.\n' "$certificate_label" >&2
        exit 1
    fi

    certificate_public_key="$(openssl x509 -in "$certificate_file" -pubkey -noout \
        | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | awk '{print $1}')"
    private_key_public_key="$(openssl pkey -in "$private_key_file" -pubout -outform DER 2>/dev/null \
        | sha256sum | awk '{print $1}')"
    if [[ -z "$certificate_public_key" || "$certificate_public_key" != "$private_key_public_key" ]]; then
        printf 'Refusing: %s certificate and private key do not match.\n' "$certificate_label" >&2
        exit 1
    fi

    awk 'BEGIN {certificate=0} /-----BEGIN CERTIFICATE-----/ {certificate++} certificate == 1 {print} /-----END CERTIFICATE-----/ && certificate == 1 {exit}' \
        "$certificate_file" > "$leaf_file"
    awk 'BEGIN {certificate=0} /-----BEGIN CERTIFICATE-----/ {certificate++} certificate >= 2 {print}' \
        "$certificate_file" > "$chain_file"
    if [[ -s "$chain_file" && -r /etc/ssl/certs/ca-certificates.crt ]] && ! openssl verify \
        -CAfile /etc/ssl/certs/ca-certificates.crt -untrusted "$chain_file" "$leaf_file" >/dev/null; then
        printf 'Refusing: %s certificate chain validation failed.\n' "$certificate_label" >&2
        exit 1
    fi
}

validate_certificate_pair 'legacy production-entry' \
    "$LEGACY_CERT_DIR/fullchain.pem" "$LEGACY_CERT_DIR/privkey.pem" "$TEMP_DIR/legacy"
legacy_san_names="$(openssl x509 -in "$LEGACY_CERT_DIR/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
if ! printf '%s\n' "$legacy_san_names" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | grep -Fx "DNS:$LEGACY_CERT_NAME" >/dev/null; then
    printf 'Refusing: legacy production-entry certificate does not contain exact SAN DNS:%s\n' "$LEGACY_CERT_NAME" >&2
    exit 1
fi

# Transfer only the two files Nginx needs; never print their contents.
staging_exec "sudo -n cat /etc/letsencrypt/live/$CERT_NAME/fullchain.pem" > "$TEMP_DIR/fullchain.pem"
staging_exec "sudo -n cat /etc/letsencrypt/live/$CERT_NAME/privkey.pem" > "$TEMP_DIR/privkey.pem"

validate_certificate_pair 'staging' "$TEMP_DIR/fullchain.pem" "$TEMP_DIR/privkey.pem" "$TEMP_DIR/staging"

san_names="$(openssl x509 -in "$TEMP_DIR/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
if ! printf '%s\n' "$san_names" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
    | grep -Fx "DNS:$CERT_NAME" >/dev/null; then
    printf 'Refusing: staging certificate does not contain exact SAN DNS:%s\n' "$CERT_NAME" >&2
    exit 1
fi

readonly BACKUP_DIR="$TEMP_DIR/backup"
install -d -o root -g root -m 0700 "$CERT_DIR" "$BACKUP_DIR"
readonly FULLCHAIN_TARGET="$(readlink -f "$CERT_DIR/fullchain.pem" 2>/dev/null || printf '%s' "$CERT_DIR/fullchain.pem")"
readonly PRIVKEY_TARGET="$(readlink -f "$CERT_DIR/privkey.pem" 2>/dev/null || printf '%s' "$CERT_DIR/privkey.pem")"
if [[ -e "$FULLCHAIN_TARGET" ]]; then cp -- "$FULLCHAIN_TARGET" "$BACKUP_DIR/fullchain.pem"; fi
if [[ -e "$PRIVKEY_TARGET" ]]; then cp -- "$PRIVKEY_TARGET" "$BACKUP_DIR/privkey.pem"; fi
atomic_replace_file() {
    local source_file="$1"
    local target_file="$2"
    local mode="$3"
    local staged_file
    staged_file="$(mktemp "${target_file}.sync.XXXXXX")"
    if ! install -o root -g root -m "$mode" "$source_file" "$staged_file"; then
        rm -f "$staged_file"
        return 1
    fi
    mv -f "$staged_file" "$target_file"
}
restore_certificate() {
    [[ -e "$BACKUP_DIR/fullchain.pem" ]] && atomic_replace_file "$BACKUP_DIR/fullchain.pem" "$FULLCHAIN_TARGET" 0644
    [[ -e "$BACKUP_DIR/privkey.pem" ]] && atomic_replace_file "$BACKUP_DIR/privkey.pem" "$PRIVKEY_TARGET" 0600
}
atomic_replace_file "$TEMP_DIR/fullchain.pem" "$FULLCHAIN_TARGET" 0644
atomic_replace_file "$TEMP_DIR/privkey.pem" "$PRIVKEY_TARGET" 0600

# Keep both production edge routes versioned: the new hostname is certificate-only,
# while the legacy hostname redirects to production.
install -o root -g root -m 0644 "$(dirname "$0")/nginx/staging-edge-certificate.conf" "$EDGE_CONFIG"
install -o root -g root -m 0644 "$(dirname "$0")/nginx/staging-legacy-production-entry.conf" "$LEGACY_EDGE_CONFIG"
if ! docker exec bytedepth-nginx-1 nginx -t; then
    restore_certificate
    exit 1
fi
if ! docker exec bytedepth-nginx-1 nginx -s reload; then
    restore_certificate
    docker exec bytedepth-nginx-1 nginx -s reload || true
    exit 1
fi
printf 'Synchronized %s certificate to the production edge.\n' "$CERT_NAME"
