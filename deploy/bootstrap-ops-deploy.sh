#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/bootstrap-ops-deploy.sh\n' >&2
    exit 1
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly GIT_REMOTE_URL=git@github.com:manfredma/bytedepth.git

require_ssh_origin() {
    local origin_url
    origin_url="$(git -c safe.directory="$SOURCE_ROOT" remote get-url origin)"
    if [[ "$origin_url" != "$GIT_REMOTE_URL" ]]; then
        printf 'Refusing deployment: origin must be %s, got %s\n' "$GIT_REMOTE_URL" "$origin_url" >&2
        exit 1
    fi
}

cd "$SOURCE_ROOT"
git_cmd() { git -c safe.directory="$SOURCE_ROOT" "$@"; }
require_ssh_origin
BYTEDEPTH_COMMIT_ID="$(git_cmd rev-parse HEAD)"
export BYTEDEPTH_COMMIT_ID
BYTEDEPTH_BUILT_AT="$(date -u +%FT%TZ)"
export BYTEDEPTH_BUILT_AT

# 安装宿主机服务、数据目录和部署 Socket。应用 JAR 由外部构建机提供，
# 本脚本不构建 Maven 项目，也不启动任何容器。
./deploy/install-host-service.sh
systemctl start mysql.service redis.service meilisearch.service
systemctl is-active --quiet mysql.service
systemctl is-active --quiet redis.service
systemctl is-active --quiet meilisearch.service
