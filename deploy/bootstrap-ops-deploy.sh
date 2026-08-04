#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/bootstrap-ops-deploy.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
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

deploy_mode=single-host
if [[ -f "$CONFIG_FILE" ]]; then
    configured_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value=$2} END {print value}' "$CONFIG_FILE")"
    deploy_mode="${configured_mode:-$deploy_mode}"
fi

case "$deploy_mode" in
    single-host)
        compose_args=()
        ;;
    data-access)
        compose_args=(-f docker-compose.yml -f deploy/docker-compose.data-access.yml)
        ;;
    external-services)
        compose_args=(--env-file .env -f deploy/docker-compose.app-external.yml)
        if ! mountpoint -q /mnt/bytedepth-images; then
            printf 'Refusing external-services deployment: /mnt/bytedepth-images is not mounted\n' >&2
            exit 1
        fi
        ;;
    *)
        printf 'Unsupported BYTEDEPTH_DEPLOY_MODE: %s\n' "$deploy_mode" >&2
        exit 1
        ;;
esac

docker compose "${compose_args[@]}" up --build -d
docker compose "${compose_args[@]}" up -d --force-recreate nginx
docker compose "${compose_args[@]}" ps
