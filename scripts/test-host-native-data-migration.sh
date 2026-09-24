#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT

for file in \
    "$ROOT/deploy/sync-prod-to-staging.sh" \
    "$ROOT/deploy/provision-staging-certificate.sh" \
    "$ROOT/deploy/sync-staging-certificate-to-production.sh" \
    "$ROOT/deploy/setup-shared-images-nfs.sh"; do
    [[ -f "$file" ]] || { printf 'Missing migration script: %s\n' "$file" >&2; exit 1; }
    if rg -n -i 'docker|compose|docker-compose' "$file" >/dev/null; then
        printf 'Host-native migration script still references Docker: %s\n' "$file" >&2
        exit 1
    fi
done

require_text() {
    local needle="$1"
    local file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing migration contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

require_text 'mysqldump' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'redis-cli' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'systemctl stop redis.service' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'meilisearch --import-snapshot' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text '--delete' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'openssl x509 -checkend' "$ROOT/deploy/provision-staging-certificate.sh"
require_text 'StrictHostKeyChecking=yes' "$ROOT/deploy/sync-staging-certificate-to-production.sh"
require_text 'nfs-server' "$ROOT/deploy/setup-shared-images-nfs.sh"
require_text 'bytedepth-images.mount' "$ROOT/deploy/setup-shared-images-nfs.sh"

printf 'Host-native data migration contract passed.\n'
