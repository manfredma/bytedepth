#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/migrate-production-docker-to-native.sh"
readonly LIB="$ROOT/deploy/lib/production-green-migration.sh"

for file in "$SCRIPT" "$LIB"; do
    [[ -f "$file" ]] || { printf 'Missing production migration file: %s\n' "$file" >&2; exit 1; }
done

require_text() {
    local needle="$1" file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing production migration contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

require_text 'prepare|final-sync|verify|rollback' "$SCRIPT"
require_text 'production_green_require_blue' "$LIB"
require_text 'bytedepth-mysql-1' "$LIB"
require_text 'bytedepth-redis-1' "$LIB"
require_text 'bytedepth-meilisearch-1' "$LIB"
require_text 'mysqldump -uroot --databases bytedepth --single-transaction --quick' "$LIB"
require_text 'gzip -1' "$LIB"
require_text 'production_green_wait_mysql' "$LIB"
require_text 'production_green_wait_redis' "$LIB"
require_text 'if mysqladmin \' "$LIB"
require_text 'MYSQL_PWD="$mysql_password" mysql --protocol=tcp' "$LIB"
require_text "if MYSQL_PWD='' mysql --protocol=tcp" "$LIB"
require_text "mysql_password=''" "$LIB"
require_text 'chmod -R g+rwX "$data_dir"' "$LIB"
require_text 'redis-cli --rdb' "$LIB"
require_text 'meilisearch --import-snapshot' "$LIB"
require_text 'curl --fail --silent --show-error --retry 30 --retry-delay 1 --retry-connrefused' "$LIB"
require_text 'rsync -a --delete /data/images/' "$LIB"
require_text 'production_green_mark syncing' "$LIB"
require_text 'blue bytedepth app must be stopped before final sync' "$LIB"
require_text 'production_green_mark_uncertain' "$SCRIPT"
require_text 'uncertain' "$LIB"
require_text "find \"\$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql\"" "$LIB"
require_text '-exec rm -rf -- {} +' "$LIB"

final_sync_start="$(rg -n '^production_green_final_sync\(\)' "$LIB" | cut -d: -f1)"
final_sync_end="$(tail -n +"$final_sync_start" "$LIB" | rg -n '^}$' | head -n 1 | cut -d: -f1)"
final_sync_end=$((final_sync_start + final_sync_end - 1))
final_sync_body="$(sed -n "${final_sync_start},${final_sync_end}p" "$LIB")"
cleanup_line="$(printf '%s\n' "$final_sync_body" | rg -n '^    find ' | cut -d: -f1)"
stack_install_line="$(printf '%s\n' "$final_sync_body" | rg -n 'install-production-green-stack\.sh' | cut -d: -f1)"
[[ -n "$cleanup_line" && -n "$stack_install_line" && "$stack_install_line" -gt "$cleanup_line" ]] || {
    printf 'Final sync must reinstall green service configuration after clearing middleware data directories.\n' >&2
    exit 1
}

if rg -n 'rm -rf -- /data|rm -rf -- /opt|DROP DATABASE.*mysql|docker compose|docker-compose' "$LIB" >/dev/null; then
    printf 'Production migration contains an unsafe broad cleanup or Compose recreation.\n' >&2
    exit 1
fi
if rg -n 'docker (stop|rm|restart)|/opt/nginx-conf\.d|nginx -s reload' "$LIB" >/dev/null; then
    printf 'Green data preparation must not stop Docker or change the public route.\n' >&2
    exit 1
fi
if rg -n 'MYSQL_PWD=.*--password|--password[ =]' "$LIB" >/dev/null; then
    printf 'Production migration places a password in a command-line argument.\n' >&2
    exit 1
fi
if rg -n 'production_green_wait_mysql[[:space:]]+\"\$db_password\"' "$LIB" >/dev/null; then
    printf 'MySQL readiness must not be coupled to the application password.\n' >&2
    exit 1
fi

bash -n "$SCRIPT" "$LIB"
printf 'Production green migration contract passed.\n'
