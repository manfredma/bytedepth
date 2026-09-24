#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly UNIT_DIR="$ROOT/deploy/systemd"
readonly INSTALLER="$ROOT/deploy/install-host-service.sh"

require_file() {
    [[ -f "$1" ]] || {
        printf 'Missing host-native runtime file: %s\n' "$1" >&2
        exit 1
    }
}

require_text() {
    local needle="$1"
    local file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing runtime contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

for unit in bytedepth-app.service bytedepth-test-slot.service mysql.service redis.service meilisearch.service nginx.service; do
    require_file "$UNIT_DIR/$unit"
done

require_text 'User=bytedepth' "$UNIT_DIR/bytedepth-app.service"
require_text 'ExecStart=/usr/lib/jvm/java-25-openjdk/bin/java' "$UNIT_DIR/bytedepth-app.service"
require_text 'RequiresMountsFor=/data/images' "$UNIT_DIR/bytedepth-app.service"
require_text 'Requires=mysql.service redis.service meilisearch.service' "$UNIT_DIR/bytedepth-app.service"
require_text 'EnvironmentFile=-/etc/bytedepth/application-production.env' "$UNIT_DIR/bytedepth-app.service"
require_text 'ReadWritePaths=/data/images' "$UNIT_DIR/bytedepth-app.service"
if rg -q 'ReadWritePaths=.*current' "$UNIT_DIR/bytedepth-app.service"; then
    printf 'Running application must not be able to modify the current release JAR.\n' >&2
    exit 1
fi
require_text 'EnvironmentFile=/run/bytedepth/staging-e2e.env' "$UNIT_DIR/bytedepth-test-slot.service"
require_text 'Conflicts=bytedepth-app.service' "$UNIT_DIR/bytedepth-test-slot.service"
require_text 'sha256sum' "$UNIT_DIR/bytedepth-test-slot.service"
require_text 'bytedepth-test-slot.service' "$INSTALLER"
require_text '/data/images-test' "$INSTALLER"

require_text 'User=mysql' "$UNIT_DIR/mysql.service"
require_text 'ExecStart=/usr/sbin/mysqld' "$UNIT_DIR/mysql.service"
require_text 'ReadWritePaths=/data/mysql' "$UNIT_DIR/mysql.service"
require_text 'User=redis' "$UNIT_DIR/redis.service"
require_text 'ExecStart=/usr/bin/redis-server /etc/redis/redis.conf' "$UNIT_DIR/redis.service"
require_text 'User=meilisearch' "$UNIT_DIR/meilisearch.service"
require_text 'ExecStart=/usr/local/bin/meilisearch --config-file-path /etc/meilisearch.toml' "$UNIT_DIR/meilisearch.service"

require_text 'Requires=bytedepth-app.service' "$UNIT_DIR/nginx.service"
require_text 'After=bytedepth-app.service' "$UNIT_DIR/nginx.service"
require_text 'ExecStartPre=/usr/bin/curl --fail' "$UNIT_DIR/nginx.service"
require_text "ExecStart=/usr/sbin/nginx -g 'daemon on; master_process on;'" "$UNIT_DIR/nginx.service"

for unit in mysql.service redis.service meilisearch.service; do
    require_text '127.0.0.1' "$UNIT_DIR/$unit"
done

if rg -n -i 'docker|compose|STAGING_MAVEN_IMAGE|prewarm-production-maven-cache' \
    "$ROOT/deploy" --glob '*.sh' --glob '*.yml' --glob '*.yaml' --glob '*.service' \
    --glob '!migrate-staging-docker-to-native.sh' >/dev/null; then
    printf 'Runtime/deployment scripts still contain Docker-only runtime contracts.\n' >&2
    exit 1
fi

printf 'Host-native runtime contract passed.\n'
