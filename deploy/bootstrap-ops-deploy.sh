#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/bootstrap-ops-deploy.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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
./deploy/install-host-service.sh

# compose 文件选择与 NFS 挂载检查统一由 ctl.sh 按部署模式处理。
./deploy/ctl.sh up --build -d
./deploy/ctl.sh up -d --force-recreate nginx
./deploy/ctl.sh ps
