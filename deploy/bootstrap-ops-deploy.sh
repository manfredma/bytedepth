#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/bootstrap-ops-deploy.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cd "$SOURCE_ROOT"
./deploy/install-host-service.sh
docker compose up --build -d
docker compose ps
