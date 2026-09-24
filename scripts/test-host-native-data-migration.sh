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

TRANSITION="$ROOT/deploy/migrate-staging-docker-to-native.sh"
[[ -x "$TRANSITION" ]] || { printf 'Missing executable staging blue/green migration script.\n' >&2; exit 1; }
require_text_transition() {
    local needle="$1"
    rg -F -- "$needle" "$TRANSITION" >/dev/null || {
        printf 'Missing blue/green migration contract: %s\n' "$needle" >&2
        exit 1
    }
}
require_text_transition 'prepare) prepare'
require_text_transition 'switch) switch_traffic'
require_text_transition 'rollback) rollback'
require_text_transition 'cleanup) cleanup'
require_text_transition 'docker exec'
require_text_transition 'BYTEDEPTH_NATIVE_CLEANUP_ACCEPTED=1'
require_text_transition '172.18.0.1:$BYTEDEPTH_NATIVE_EDGE_PORT'
require_text_transition 'native MySQL data path is not empty'
if rg -n 'docker compose|docker-compose' "$TRANSITION" >/dev/null; then
    printf 'Blue/green transition must not recreate the old Compose stack.\n' >&2
    exit 1
fi

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
