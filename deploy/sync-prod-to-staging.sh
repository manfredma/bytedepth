#!/usr/bin/env bash
# 生产(175)→staging(124) 数据同步：MySQL/Redis/MeiliSearch/图片 全量覆盖。
# 在 175 上执行，推送到 124。同步期间停止 staging app。
# 用法：sudo ./deploy/sync-prod-to-staging.sh
#
# 配置文件 /etc/bytedepth-sync.conf（root 0600）需包含：
#   SYNC_SSH_KEY=/home/ubuntu/.ssh/bytedepth_sync
#   STAGING_IP=10.0.0.5
# MySQL/Redis/MeiliSearch 的源端凭据来自 175 的 .env；
# 目标端（124）的凭据由 124 本地 .env 提供（导入时在 124 上 source）。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/sync-prod-to-staging.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ENV_FILE="$SOURCE_ROOT/.env"
readonly SYNC_CONF=/etc/bytedepth-sync.conf
readonly LOG=/var/log/bytedepth/sync-prod-to-staging.log
readonly LOCK_FILE=/var/lock/bytedepth-sync.lock

# 读同步配置（SSH key 与 staging IP）
if [[ ! -r "$SYNC_CONF" ]]; then
    printf 'Missing %s. Create it with SYNC_SSH_KEY and STAGING_IP.\n' "$SYNC_CONF" >&2
    exit 1
fi
# shellcheck disable=SC1090
. "$SYNC_CONF"
readonly STAGING_USER=ubuntu@${STAGING_IP:?STAGING_IP must be set in $SYNC_CONF}
readonly SSH_KEY=${SYNC_SSH_KEY:?SYNC_SSH_KEY must be set in $SYNC_CONF}
# 显式 SSH 选项（不依赖 ~/.ssh/config，sudo/cron 下可用）
SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new)

mkdir -p "$(dirname "$LOG")" "$(dirname "$LOCK_FILE")"

exec 9>"$LOCK_FILE"
if ! flock -xn 9; then
    printf 'Another sync is running\n' >&2
    exit 1
fi

# 加载 175 .env（源端 DB_PASSWORD/REDIS_PASSWORD/MEILI_MASTER_KEY）
set -a
. "$ENV_FILE"
set +a

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG"; }

trap 'log "同步异常退出"' ERR

log "===== 开始同步 ====="

# 在 124 执行命令（source 124 本地 .env 获取目标端密码）
staging_exec() {
    ssh "${SSH_OPTS[@]}" "$STAGING_USER" "$1"
}
# 传文件到 124
staging_send() {
    scp "${SSH_OPTS[@]}" "$1" "$STAGING_USER:$2"
}

# 同步会清空 staging 的写测试数据（drop+create 库）。cron 触发时直接执行。

# --- 停止 staging app（避免导入时 app 继续写、且新代码与旧 schema 不兼容）---
log "停止 staging app..."
staging_exec "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop app" || true

# --- MySQL ---
log "MySQL: 导出生产（--single-transaction 一致性快照）..."
DUMP=$(mktemp /tmp/bytedepth-sync-XXXX.sql)
chmod 600 "$DUMP"
docker exec bytedepth-mysql-1 mysqldump --single-transaction --quick \
    --routines --events --triggers --no-tablespaces \
    -u root -p"$DB_PASSWORD" bytedepth > "$DUMP"
log "MySQL: 传输到 124..."
staging_send "$DUMP" "/tmp/bytedepth-sync.sql"
log "MySQL: 导入到 staging（drop+create 库，用 124 本地密码）..."
# 在 124 上 source .env 获取目标端 DB_PASSWORD（与生产不同）
staging_exec 'set -a && . /opt/bytedepth/.env && set +a && \
    docker exec -i bytedepth-mysql-1 mysql -u root -p"$DB_PASSWORD" -e "DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" && \
    docker exec -i bytedepth-mysql-1 mysql -u root -p"$DB_PASSWORD" bytedepth < /tmp/bytedepth-sync.sql && \
    rm /tmp/bytedepth-sync.sql'
rm -f "$DUMP"
log "MySQL: 完成"

# --- Redis ---
# Redis 7 用 appendonlydir + manifest。必须先清空目标 AOF/RDB，否则旧数据残留。
log "Redis: 停止 staging redis..."
staging_exec "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop redis"
log "Redis: 清空 staging redis 数据目录..."
staging_exec "sudo rm -rf /data/redis/dump.rdb /data/redis/appendonlydir /data/redis/appendonly.aof.* /data/redis/manifest"
log "Redis: 生产 BGSAVE..."
docker exec bytedepth-redis-1 redis-cli -a "$REDIS_PASSWORD" BGSAVE
while [ "$(docker exec bytedepth-redis-1 redis-cli -a "$REDIS_PASSWORD" INFO persistence 2>/dev/null | grep rdb_bgsave_in_progress | tr -d '\r' | cut -d: -f2)" != "0" ]; do
    sleep 1
done
log "Redis: 传输 dump.rdb..."
docker cp bytedepth-redis-1:/data/dump.rdb /tmp/bytedepth-sync-dump.rdb
staging_send /tmp/bytedepth-sync-dump.rdb "/tmp/dump.rdb"
rm -f /tmp/bytedepth-sync-dump.rdb
staging_exec "sudo mv /tmp/dump.rdb /data/redis/dump.rdb && cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d redis"
log "Redis: 完成（以 RDB 启动，重建 AOF）"

# --- MeiliSearch ---
# snapshot 放目录后重启不会导入，必须 --import-snapshot 启动，且清空旧 DB。
# MeiliSearch v1.7：POST /snapshots 触发异步 task，snapshot 文件写到磁盘
# /data/meilisearch/snapshots/，不通过 API 下载，直接从磁盘拷贝。
log "MeiliSearch: 停止 staging meili..."
staging_exec "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop meilisearch"
log "MeiliSearch: 清空 staging DB..."
staging_exec "sudo rm -rf /data/meilisearch/data.ms"
log "MeiliSearch: 生产创建 snapshot（异步 task）..."
# data-access 模式下 MeiliSearch 绑定内网 IP，非 127.0.0.1
MEILI_BIND_IP=${BYTEDEPTH_DATA_BIND_IP:-127.0.0.1}
MEILI_URL="http://$MEILI_BIND_IP:7700"
TASK_UID=$(curl -s -X POST "$MEILI_URL/snapshots" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"taskUid":[0-9]*' | cut -d: -f2)
if [[ -z "$TASK_UID" ]]; then
    log "ERROR: 创建 snapshot task 失败"
    exit 1
fi
log "MeiliSearch: 等待 snapshot task $TASK_UID 完成..."
while true; do
    TASK_STATUS=$(curl -s "$MEILI_URL/tasks/$TASK_UID" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    [[ "$TASK_STATUS" == "succeeded" ]] && break
    [[ "$TASK_STATUS" == "failed" ]] && { log "ERROR: snapshot task failed"; exit 1; }
    sleep 2
done
log "MeiliSearch: 从生产磁盘拷贝 snapshot 文件..."
# snapshot 文件在容器的 /meili_data/snapshots/，宿主机映射到 /data/meilisearch/snapshots/
SNAP_SRC=$(docker exec bytedepth-meilisearch-1 sh -c 'ls /meili_data/snapshots/*.snapshot 2>/dev/null | sort | tail -1')
if [[ -z "$SNAP_SRC" ]]; then
    log "ERROR: 未找到 snapshot 文件"
    exit 1
fi
docker cp "bytedepth-meilisearch-1:$SNAP_SRC" /tmp/meili-snapshot
staging_send /tmp/meili-snapshot "/tmp/meili-snapshot"
rm -f /tmp/meili-snapshot
log "MeiliSearch: 导入 snapshot（一次性 docker run --import-snapshot）..."
staging_exec "sudo mv /tmp/meili-snapshot /data/meilisearch/snapshot.snapshot && sudo docker run --rm --entrypoint /bin/sh -v /data/meilisearch:/data getmeili/meilisearch:v1.7 -c 'meilisearch --import-snapshot /data/snapshot.snapshot --db-path /data/data.ms' && sudo rm /data/meilisearch/snapshot.snapshot"
log "MeiliSearch: 启动 staging meili..."
staging_exec "cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d meilisearch"
log "MeiliSearch: 完成"

# --- 图片 ---
log "图片: rsync 同步（--delete 保持一致）..."
rsync -avz --delete -e "ssh ${SSH_OPTS[*]}" /data/images/ "$STAGING_USER:/data/images/"
log "图片: 完成"

# --- 恢复 staging app ---
log "启动 staging app（Flyway 自动迁移）..."
staging_exec "cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d app"

# --- 验证 ---
log "验证 staging..."
sleep 15
HTTP=$(staging_exec "curl -ksS -o /dev/null -w '%{http_code}' https://staging.bytedepth.cn/")
if [ "$HTTP" != "200" ]; then
    log "ERROR: staging 返回 $HTTP，同步可能失败"
    exit 1
fi
log "===== 同步完成，staging 返回 200 ====="
