#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly TARGET_HELPER="$ROOT/deploy/lib/production-green-target.sh"
readonly CONFIG_EXAMPLE="$ROOT/deploy/production-green.conf.example"
readonly INSTALLER="$ROOT/deploy/install-production-green-stack.sh"

require_file() {
    [[ -f "$1" ]] || {
        printf 'Missing production green runtime file: %s\n' "$1" >&2
        exit 1
    }
}

require_text() {
    local needle="$1"
    local file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing production green contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

require_file "$TARGET_HELPER"
require_file "$CONFIG_EXAMPLE"

for unit in mysql redis meilisearch app edge; do
    require_file "$ROOT/deploy/systemd/bytedepth-production-green-$unit.service.in"
done

require_text 'BYTEDEPTH_PRODUCTION_GREEN_ROOT=/data/bytedepth-native-production' "$CONFIG_EXAMPLE"
require_text 'BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT=13306' "$CONFIG_EXAMPLE"
require_text 'BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT=16379' "$CONFIG_EXAMPLE"
require_text 'BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT=17700' "$CONFIG_EXAMPLE"
require_text 'BYTEDEPTH_PRODUCTION_GREEN_APP_PORT=18080' "$CONFIG_EXAMPLE"
require_text 'BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT=18081' "$CONFIG_EXAMPLE"

require_text 'load_production_green_target()' "$TARGET_HELPER"
require_text '/data/bytedepth-native-production' "$TARGET_HELPER"
require_text 'bytedepth-production-green-app.service' "$TARGET_HELPER"
require_text 'bytedepth-production-green-edge.service' "$TARGET_HELPER"

for unit in mysql redis meilisearch app edge; do
    unit_file="$ROOT/deploy/systemd/bytedepth-production-green-$unit.service.in"
    require_text 'bytedepth-production-green-' "$unit_file"
    require_text 'User=' "$unit_file"
    require_text 'MemoryMax=' "$unit_file"
done
require_text 'User=bytedepth' "$ROOT/deploy/systemd/bytedepth-production-green-app.service.in"
require_text 'User=ubuntu' "$ROOT/deploy/systemd/bytedepth-production-green-edge.service.in"
require_text 'RequiresMountsFor=__GREEN_ROOT__/images' "$ROOT/deploy/systemd/bytedepth-production-green-app.service.in"
require_text 'EnvironmentFile=/etc/bytedepth/production-green.env' "$ROOT/deploy/systemd/bytedepth-production-green-app.service.in"
require_text '127.0.0.1:__APP_PORT__' "$ROOT/deploy/systemd/bytedepth-production-green-edge.service.in"
require_text '/edge/nginx.pid' "$INSTALLER"
require_text '/edge/error.log' "$INSTALLER"
require_text 'client_body_temp_path $BYTEDEPTH_PRODUCTION_GREEN_ROOT/edge/client_body_temp;' "$INSTALLER"
require_text 'proxy_temp_path $BYTEDEPTH_PRODUCTION_GREEN_ROOT/edge/proxy_temp;' "$INSTALLER"
require_text 'edge/client_body_temp' "$INSTALLER"
require_text 'edge/proxy_temp' "$INSTALLER"
require_text 'apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld' "$INSTALLER"
require_text 'bytedepth-production-green/** rwk' "$INSTALLER"
require_text 'mysql-server redis-server nginx-core' "$INSTALLER"
require_text 'command -v mysqld' "$INSTALLER"
require_text 'command -v redis-server' "$INSTALLER"
require_text 'command -v nginx' "$INSTALLER"
require_text 'nginx-core' "$INSTALLER"
require_text 'systemctl mask nginx.service' "$INSTALLER"
require_text 'systemctl unmask nginx.service' "$INSTALLER"
require_text 'vm.overcommit_memory = 1' "$INSTALLER"
require_text 'sysctl -w vm.overcommit_memory=1' "$INSTALLER"
require_text 'Type=simple' "$ROOT/deploy/systemd/bytedepth-production-green-redis.service.in"

require_text 'production-green' "$INSTALLER"
require_text 'ubuntu:ubuntu' "$INSTALLER"

for forbidden in '3306' '6379' '7700' '8080' '80' '443'; do
    if rg -n "^[[:space:]]*(BYTEDEPTH_PRODUCTION_GREEN_[A-Z_]+|[a-z_]+_port)=?$forbidden$" "$CONFIG_EXAMPLE" >/dev/null; then
        printf 'Production green configuration reuses a default port: %s\n' "$forbidden" >&2
        exit 1
    fi
done

printf 'Production green runtime contract passed.\n'
