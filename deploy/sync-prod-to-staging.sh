#!/usr/bin/env bash
# 生产(175)到 staging(124) 的宿主机数据同步：MySQL、Redis、Meilisearch 和图片。
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

[[ -r "$SYNC_CONF" ]] || { printf 'Missing %s.\n' "$SYNC_CONF" >&2; exit 1; }
[[ -f "$SYNC_CONF" && "$(stat -c '%U:%G:%a' "$SYNC_CONF")" == 'root:root:600' ]] || {
    printf 'Refusing: %s must be root-owned mode 0600.\n' "$SYNC_CONF" >&2; exit 1;
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

mkdir -p "$(dirname "$LOG")" "$(dirname "$LOCK_FILE")"
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

log "同步 staging 证书到生产边缘..."
"$SOURCE_ROOT/deploy/sync-staging-certificate-to-production.sh"
log "停止 staging 应用..."
staging_exec 'sudo systemctl stop bytedepth-app.service' || true

dump="$(mktemp /tmp/bytedepth-sync-XXXX.sql)"
chmod 600 "$dump"
log "MySQL 导出与导入..."
mysqldump --defaults-extra-file="$SOURCE_MYSQL_CNF" --single-transaction --quick \
    --routines --events --triggers --no-tablespaces bytedepth > "$dump"
staging_send "$dump" /tmp/bytedepth-sync.sql
staging_exec 'sudo mysql --defaults-extra-file=/etc/bytedepth/mysql-target.cnf -e "DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" && sudo mysql --defaults-extra-file=/etc/bytedepth/mysql-target.cnf bytedepth < /tmp/bytedepth-sync.sql && sudo rm -f /tmp/bytedepth-sync.sql'
rm -f "$dump"

log "Redis 导出与导入..."
redis_dump="$(mktemp /tmp/bytedepth-redis-XXXX.rdb)"
chmod 600 "$redis_dump"
REDISCLI_AUTH="${REDIS_PASSWORD:?REDIS_PASSWORD must be set}" redis-cli -h 127.0.0.1 -p 6379 --rdb "$redis_dump" >/dev/null
staging_send "$redis_dump" /tmp/bytedepth-sync.rdb
rm -f "$redis_dump"
staging_exec 'sudo systemctl stop redis.service && sudo rm -rf /data/redis/dump.rdb /data/redis/appendonlydir && sudo install -o redis -g redis -m 0640 /tmp/bytedepth-sync.rdb /data/redis/dump.rdb && sudo rm -f /tmp/bytedepth-sync.rdb && sudo systemctl start redis.service'

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
staging_exec 'sudo systemctl stop meilisearch.service && sudo rm -rf /data/meilisearch/data.ms && sudo timeout 120 /usr/local/bin/meilisearch --import-snapshot /tmp/bytedepth-sync.snapshot --db-path /data/meilisearch/data.ms && sudo rm -f /tmp/bytedepth-sync.snapshot && sudo systemctl start meilisearch.service'

log "图片同步..."
rsync -avz --delete --rsync-path="sudo rsync" -e "ssh ${SSH_OPTS[*]}" /data/images/ "$STAGING_USER:/data/images/"

log "恢复 staging 应用并验证..."
staging_exec 'sudo systemctl start bytedepth-app.service && sudo systemctl reload nginx.service'
sleep 15
http_code="$(staging_exec "curl -ksS -o /dev/null -w '%{http_code}' 'https://staging-bytedepth.bytedepth.cn/'")"
[[ "$http_code" == 200 ]] || { printf 'staging returned HTTP %s.\n' "$http_code" >&2; exit 1; }
log "同步完成，staging 返回 200。"
