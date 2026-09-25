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
require_text_transition 'ensure_meilisearch_runtime'
require_text_transition 'apt-get install -y --no-install-recommends musl'
require_text_transition '/usr/lib/x86_64-linux-musl/libgcc_s.so.1'
require_text_transition 'docker exec "$DOCKER_MEILI" /bin/meilisearch --version'
require_text_transition 'docker exec "$DOCKER_MEILI" /bin/sh -c '\''cat /bin/meilisearch'\'''
require_text_transition 'docker exec "$DOCKER_MEILI" /bin/sh -c '\''curl --fail --silent --show-error -X POST'
require_text_transition 'command -v jq >/dev/null'
require_text_transition 'for _ in {1..600}'
require_text_transition 'indexes/posts'
require_text_transition 'meilisearch-import'
require_text_transition 'meilisearch.toml'
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
require_text 'BYTEDEPTH_NATIVE_STACK_MODE' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'bytedepth-staging-native-redis.service' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'staging-native-mysql-admin.cnf' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text '/data/bytedepth-native-staging' "$ROOT/deploy/sync-prod-to-staging.sh"
if rg -n "systemctl (stop|start) (mysql|redis|meilisearch|bytedepth-app|nginx)\.service|mysql-target\.cnf|/data/redis/(dump|appendonly)|/data/meilisearch/data\.ms" "$ROOT/deploy/sync-prod-to-staging.sh" >/dev/null; then
    printf 'Production-to-staging sync must not target the retired canonical staging stack.\n' >&2
    exit 1
fi
require_text 'meilisearch --import-snapshot' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text '--delete' "$ROOT/deploy/sync-prod-to-staging.sh"
require_text 'openssl x509 -checkend' "$ROOT/deploy/provision-staging-certificate.sh"
require_text 'StrictHostKeyChecking=yes' "$ROOT/deploy/sync-staging-certificate-to-production.sh"
require_text 'nfs-server' "$ROOT/deploy/setup-shared-images-nfs.sh"
require_text 'bytedepth-images.mount' "$ROOT/deploy/setup-shared-images-nfs.sh"

printf 'Host-native data migration contract passed.\n'
