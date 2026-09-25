#!/usr/bin/env bash
# 生产到 staging native 隔离栈的数据同步：MySQL、Redis、Meilisearch 和图片。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/sync-prod-to-staging.sh\n' >&2
    exit 1
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly ENV_FILE="$SOURCE_ROOT/.env"
readonly SYNC_CONF=/etc/bytedepth-sync.conf
readonly LOG=/var/log/bytedepth/sync-prod-to-staging.log
readonly LOCK_FILE=/var/lock/bytedepth-sync.lock
readonly SSH_KNOWN_HOSTS=/root/.ssh/known_hosts
readonly SOURCE_MYSQL_CNF=/etc/bytedepth/mysql-source.cnf
readonly STAGING_NATIVE_CONF=/etc/bytedepth/staging-native.conf
readonly STAGING_NATIVE_MYSQL_CNF=/etc/bytedepth/staging-native-mysql-admin.cnf
readonly STAGING_NATIVE_ROOT=/data/bytedepth-native-staging

[[ -r "$SYNC_CONF" ]] || { printf 'Missing %s.\n' "$SYNC_CONF" >&2; exit 1; }
[[ -f "$SYNC_CONF" && "$(stat -c '%U:%G:%a' "$SYNC_CONF")" == 'ubuntu:ubuntu:600' ]] || {
    printf 'Refusing: %s must be ubuntu-owned mode 0600.\n' "$SYNC_CONF" >&2; exit 1;
}
# shellcheck disable=SC1090
. "$SYNC_CONF"
readonly STAGING_USER=ubuntu@${STAGING_IP:?STAGING_IP must be set in $SYNC_CONF}
readonly SSH_KEY=${SYNC_SSH_KEY:?SYNC_SSH_KEY must be set in $SYNC_CONF}
[[ -f "$SSH_KEY" && "$(stat -L -c '%a' "$SSH_KEY")" == 600 ]] || {
    printf 'Refusing: SYNC_SSH_KEY must be a regular file with mode 0600.\n' >&2; exit 1;
}
[[ -r "$SSH_KNOWN_HOSTS" ]] || { printf 'Missing known_hosts: %s\n' "$SSH_KNOWN_HOSTS" >&2; exit 1; }
readonly SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes \
    -o UserKnownHostsFile="$SSH_KNOWN_HOSTS" -o StrictHostKeyChecking=yes)

install -d -o ubuntu -g ubuntu -m 0700 "$(dirname "$LOG")" "$(dirname "$LOCK_FILE")"
touch "$LOG" "$LOCK_FILE"
chown ubuntu:ubuntu "$LOG" "$LOCK_FILE"
exec 9>"$LOCK_FILE"
flock -xn 9 || { printf 'Another sync is running.\n' >&2; exit 1; }
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG"; }
trap 'log "同步异常退出"' ERR

staging_exec() { ssh "${SSH_OPTS[@]}" "$STAGING_USER" "$1"; }
staging_send() { scp "${SSH_OPTS[@]}" "$1" "$STAGING_USER:$2"; }

log "校验 staging native 隔离栈..."
staging_exec "set -Eeuo pipefail; test -r '$STAGING_NATIVE_CONF'; . '$STAGING_NATIVE_CONF'; test \"\${BYTEDEPTH_NATIVE_STACK_MODE:-}\" = parallel; test \"\${BYTEDEPTH_NATIVE_ROOT:-}\" = '$STAGING_NATIVE_ROOT'; test -r '$STAGING_NATIVE_MYSQL_CNF'; sudo nginx -t >/dev/null; sudo grep -Fq 'proxy_pass http://127.0.0.1:18081;' /etc/nginx/conf.d/bytedepth-staging.conf; for unit in bytedepth-staging-native-mysql.service bytedepth-staging-native-redis.service bytedepth-staging-native-meilisearch.service bytedepth-staging-native-app.service bytedepth-staging-native-edge.service; do systemctl cat \"\$unit\" >/dev/null; done; for unit in mysql.service redis.service meilisearch.service bytedepth-app.service; do ! systemctl is-active --quiet \"\$unit\"; done"

log "同步 staging 证书到生产边缘..."
"$SOURCE_ROOT/deploy/sync-staging-certificate-to-production.sh"
log "停止 staging 应用..."
staging_exec 'sudo systemctl stop bytedepth-staging-native-edge.service bytedepth-staging-native-app.service'

dump="$(mktemp /tmp/bytedepth-sync-XXXX.sql)"
chown ubuntu:ubuntu "$dump"
chmod 600 "$dump"
log "MySQL 导出与导入..."
mysqldump --defaults-extra-file="$SOURCE_MYSQL_CNF" --single-transaction --quick \
    --routines --events --triggers --no-tablespaces bytedepth > "$dump"
staging_send "$dump" /tmp/bytedepth-sync.sql
staging_exec "set -Eeuo pipefail; sudo mysql --defaults-extra-file='$STAGING_NATIVE_MYSQL_CNF' -e 'DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'; sudo mysql --defaults-extra-file='$STAGING_NATIVE_MYSQL_CNF' bytedepth < /tmp/bytedepth-sync.sql; sudo rm -f /tmp/bytedepth-sync.sql"
rm -f "$dump"

log "Redis 导出与导入..."
redis_dump="$(mktemp /tmp/bytedepth-redis-XXXX.rdb)"
chown ubuntu:ubuntu "$redis_dump"
chmod 600 "$redis_dump"
REDISCLI_AUTH="${REDIS_PASSWORD:?REDIS_PASSWORD must be set}" redis-cli -h 127.0.0.1 -p 6379 --rdb "$redis_dump" >/dev/null
staging_send "$redis_dump" /tmp/bytedepth-sync.rdb
rm -f "$redis_dump"
staging_exec "set -Eeuo pipefail; sudo systemctl stop bytedepth-staging-native-redis.service; sudo rm -rf '$STAGING_NATIVE_ROOT/redis/dump.rdb' '$STAGING_NATIVE_ROOT/redis/appendonlydir'; sudo install -o ubuntu -g redis -m 0660 /tmp/bytedepth-sync.rdb '$STAGING_NATIVE_ROOT/redis/dump.rdb'; sudo chmod -R g+rwX '$STAGING_NATIVE_ROOT/redis'; sudo rm -f /tmp/bytedepth-sync.rdb; sudo systemctl start bytedepth-staging-native-redis.service"

log "Meilisearch 创建并传输 snapshot..."
meili_url="${BYTEDEPTH_SEARCH_URL:-http://127.0.0.1:7700}"
task_uid="$(curl --fail --silent --show-error -X POST "$meili_url/snapshots" -H "Authorization: Bearer ${MEILI_MASTER_KEY:?MEILI_MASTER_KEY must be set}" | sed -n 's/.*"taskUid":\([0-9]*\).*/\1/p')"
[[ -n "$task_uid" ]] || { printf 'Meilisearch snapshot task was not created.\n' >&2; exit 1; }
while :; do
    task_status="$(curl --fail --silent --show-error "$meili_url/tasks/$task_uid" -H "Authorization: Bearer $MEILI_MASTER_KEY" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')"
    [[ "$task_status" == succeeded ]] && break
    [[ "$task_status" == failed ]] && { printf 'Meilisearch snapshot failed.\n' >&2; exit 1; }
    sleep 2
done
snapshot="$(find /data/meilisearch/snapshots -maxdepth 1 -type f -name '*.snapshot' -print | sort | tail -n 1)"
[[ -n "$snapshot" ]] || { printf 'Meilisearch snapshot file was not found.\n' >&2; exit 1; }
staging_send "$snapshot" /tmp/bytedepth-sync.snapshot
staging_exec "set -Eeuo pipefail; sudo systemctl stop bytedepth-staging-native-meilisearch.service; sudo rm -rf '$STAGING_NATIVE_ROOT/meilisearch-import'; sudo install -d -o ubuntu -g meilisearch -m 0770 '$STAGING_NATIVE_ROOT/meilisearch-import'; sudo timeout 300 /usr/local/bin/meilisearch --import-snapshot /tmp/bytedepth-sync.snapshot --db-path '$STAGING_NATIVE_ROOT/meilisearch-import'; sudo rm -rf '$STAGING_NATIVE_ROOT/meilisearch'; sudo install -d -o ubuntu -g meilisearch -m 0770 '$STAGING_NATIVE_ROOT/meilisearch'; sudo cp -a '$STAGING_NATIVE_ROOT/meilisearch-import'/. '$STAGING_NATIVE_ROOT/meilisearch/'; printf '%s\\n' 'env = \"production\"' | sudo tee '$STAGING_NATIVE_ROOT/meilisearch/meilisearch.toml' >/dev/null; sudo chown -R ubuntu:meilisearch '$STAGING_NATIVE_ROOT/meilisearch'; sudo chmod -R g+rwX '$STAGING_NATIVE_ROOT/meilisearch'; sudo rm -rf '$STAGING_NATIVE_ROOT/meilisearch-import'; sudo rm -f /tmp/bytedepth-sync.snapshot; sudo systemctl start bytedepth-staging-native-meilisearch.service"

log "图片同步..."
rsync -avz --delete --rsync-path="sudo rsync" -e "ssh ${SSH_OPTS[*]}" /data/images/ "$STAGING_USER:$STAGING_NATIVE_ROOT/images/"

log "恢复 staging 应用并验证..."
staging_exec 'sudo systemctl start bytedepth-staging-native-app.service bytedepth-staging-native-edge.service; sudo systemctl reload nginx.service'
sleep 15
http_code="$(staging_exec "curl -ksS -o /dev/null -w '%{http_code}' 'https://staging-bytedepth.bytedepth.cn/'")"
[[ "$http_code" == 200 ]] || { printf 'staging returned HTTP %s.\n' "$http_code" >&2; exit 1; }
log "同步完成，staging 返回 200。"
