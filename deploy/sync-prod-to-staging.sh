#!/usr/bin/env bash
# 生产(175)→staging(124) 数据同步：MySQL/Redis/MeiliSearch/图片 全量覆盖。
# 在 175 上执行，推送到 124。同步期间停止 staging app。
# 用法：sudo ./deploy/sync-prod-to-staging.sh
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/sync-prod-to-staging.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ENV_FILE="$SOURCE_ROOT/.env"
readonly STAGING_HOST=bytedepth-staging
readonly LOG=/var/log/bytedepth/sync-prod-to-staging.log
readonly LOCK_FILE=/var/lock/bytedepth-sync.lock

mkdir -p "$(dirname "$LOG")" "$(dirname "$LOCK_FILE")"

exec 9>"$LOCK_FILE"
if ! flock -xn 9; then
    printf 'Another sync is running\n' >&2
    exit 1
fi

# 加载 .env（含 DB_PASSWORD/REDIS_PASSWORD/MEILI_MASTER_KEY）
set -a
. "$ENV_FILE"
set +a

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG"; }

trap 'log "同步异常退出"' ERR

log "===== 开始同步 ====="

# 同步会清空 staging 的写测试数据（drop+create 库）。cron 触发时直接执行。

# --- 停止 staging app（避免导入时 app 继续写、且新代码与旧 schema 不兼容）---
log "停止 staging app..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop app" || true

# --- MySQL ---
log "MySQL: 导出生产（--single-transaction 一致性快照）..."
DUMP=$(mktemp /tmp/bytedepth-sync-XXXX.sql)
chmod 600 "$DUMP"
docker exec bytedepth-mysql-1 mysqldump --single-transaction --quick \
    --routines --events --triggers --no-tablespaces \
    -u root -p"$DB_PASSWORD" bytedepth > "$DUMP"
log "MySQL: 传输到 124..."
scp "$DUMP" "$STAGING_HOST:/tmp/bytedepth-sync.sql"
log "MySQL: 导入到 staging（drop+create 库）..."
ssh "$STAGING_HOST" "docker exec -i bytedepth-mysql-1 mysql -u root -p\"$DB_PASSWORD\" -e \"DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\" && docker exec -i bytedepth-mysql-1 mysql -u root -p\"$DB_PASSWORD\" bytedepth < /tmp/bytedepth-sync.sql && rm /tmp/bytedepth-sync.sql"
rm -f "$DUMP"
log "MySQL: 完成"

# --- Redis ---
# Redis 7 用 appendonlydir + manifest。必须先清空目标 AOF/RDB，否则旧数据残留。
log "Redis: 停止 staging redis..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop redis"
log "Redis: 清空 staging redis 数据目录..."
ssh "$STAGING_HOST" "sudo rm -rf /data/redis/dump.rdb /data/redis/appendonlydir /data/redis/appendonly.aof.* /data/redis/manifest"
log "Redis: 生产 BGSAVE..."
docker exec bytedepth-redis-1 redis-cli -a "$REDIS_PASSWORD" BGSAVE
while [ "$(docker exec bytedepth-redis-1 redis-cli -a "$REDIS_PASSWORD" INFO persistence 2>/dev/null | grep rdb_bgsave_in_progress | tr -d '\r' | cut -d: -f2)" != "0" ]; do
    sleep 1
done
log "Redis: 传输 dump.rdb..."
docker cp bytedepth-redis-1:/data/dump.rdb /tmp/bytedepth-sync-dump.rdb
scp /tmp/bytedepth-sync-dump.rdb "$STAGING_HOST:/tmp/dump.rdb"
rm -f /tmp/bytedepth-sync-dump.rdb
ssh "$STAGING_HOST" "sudo mv /tmp/dump.rdb /data/redis/dump.rdb && cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d redis"
log "Redis: 完成（以 RDB 启动，重建 AOF）"

# --- MeiliSearch ---
# snapshot 放目录后重启不会导入，必须 --import-snapshot 启动，且清空旧 DB。
log "MeiliSearch: 停止 staging meili..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh stop meilisearch"
log "MeiliSearch: 清空 staging DB..."
ssh "$STAGING_HOST" "sudo rm -rf /data/meilisearch/data.ms"
log "MeiliSearch: 生产创建 snapshot..."
SNAP_UID=$(curl -s -X POST "http://127.0.0.1:7700/snapshots" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"uid":"[^"]*"' | cut -d'"' -f4)
while [ "$(curl -s "http://127.0.0.1:7700/snapshots/$SNAP_UID" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)" != "succeeded" ]; do
    sleep 2
done
SNAP_DL=$(curl -s "http://127.0.0.1:7700/snapshots/$SNAP_UID" -H "Authorization: Bearer $MEILI_MASTER_KEY" | grep -o '"downloadUrl":"[^"]*"' | cut -d'"' -f4)
log "MeiliSearch: 下载并传输 snapshot..."
curl -s -o /tmp/meili-snapshot "http://127.0.0.1:7700$SNAP_DL" -H "Authorization: Bearer $MEILI_MASTER_KEY"
scp /tmp/meili-snapshot "$STAGING_HOST:/tmp/meili-snapshot"
rm -f /tmp/meili-snapshot
log "MeiliSearch: 导入 snapshot（一次性 docker run --import-snapshot）..."
ssh "$STAGING_HOST" "sudo mv /tmp/meili-snapshot /data/meilisearch/snapshot.snapshot && sudo docker run --rm -v /data/meilisearch:/data getmeili/meilisearch:v1.7 --import-snapshot /data/snapshot.snapshot --db-path /data/data.ms && sudo rm /data/meilisearch/snapshot.snapshot"
log "MeiliSearch: 启动 staging meili..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d meilisearch"
log "MeiliSearch: 完成"

# --- 图片 ---
log "图片: rsync 同步（--delete 保持一致）..."
rsync -avz --delete -e "ssh" /data/images/ "$STAGING_HOST:/data/images/"
log "图片: 完成"

# --- 恢复 staging app ---
log "启动 staging app（Flyway 自动迁移）..."
ssh "$STAGING_HOST" "cd /opt/bytedepth && sudo ./deploy/ctl.sh up -d app"

# --- 验证 ---
log "验证 staging..."
sleep 15
HTTP=$(ssh "$STAGING_HOST" "curl -ksS -o /dev/null -w '%{http_code}' https://staging.bytedepth.cn/")
if [ "$HTTP" != "200" ]; then
    log "ERROR: staging 返回 $HTTP，同步可能失败"
    exit 1
fi
log "===== 同步完成，staging 返回 200 ====="
