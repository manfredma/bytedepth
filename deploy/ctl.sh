#!/usr/bin/env bash
set -Eeuo pipefail

# bytedepth 统一的 Docker Compose 操作入口。
#
# 按 /etc/bytedepth-deploy.conf 中的 BYTEDEPTH_DEPLOY_MODE 自动选择正确的
# compose 文件，避免裸跑 `docker compose` 误读非当前部署模式的编排定义
# （例如应用节点误读根目录单机版 compose 导致 MEILI_MASTER_KEY 报错）。
#
# 用法：
#   sudo ./deploy/ctl.sh ps
#   sudo ./deploy/ctl.sh logs app --tail=100
#   sudo ./deploy/ctl.sh config
#   sudo ./deploy/ctl.sh up --build -d

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf

cd "$SOURCE_ROOT"

deploy_mode=single-host
if [[ -f "$CONFIG_FILE" ]]; then
    configured_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value=$2} END {print value}' "$CONFIG_FILE")"
    deploy_mode="${configured_mode:-$deploy_mode}"
fi

case "$deploy_mode" in
    single-host)
        compose_args=(-f deploy/docker-compose.single-host.yml)
        ;;
    data-access)
        compose_args=(-f deploy/docker-compose.single-host.yml -f deploy/docker-compose.data-access.yml)
        ;;
    external-services)
        compose_args=(-f deploy/docker-compose.app-external.yml)
        if ! mountpoint -q /mnt/bytedepth-images; then
            printf 'Refusing external-services operation: /mnt/bytedepth-images is not mounted\n' >&2
            exit 1
        fi
        ;;
    *)
        printf 'Unsupported BYTEDEPTH_DEPLOY_MODE: %s\n' "$deploy_mode" >&2
        exit 1
        ;;
esac

# .env 固定在仓库根目录。必须显式指定 --env-file：
# compose 文件在 deploy/ 下时，项目目录会变为 deploy/，默认不再读取根目录 .env。
docker compose --env-file .env "${compose_args[@]}" "$@"
