#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/migrate-production-docker-to-native.sh {prepare|final-sync|verify|rollback}\n' >&2
    exit 1
fi
[[ $# -eq 1 && "$1" =~ ^(prepare|final-sync|verify|rollback)$ ]] || {
    printf 'Usage: %s {prepare|final-sync|verify|rollback}\n' "$0" >&2
    exit 2
}

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT=/var/lib/bytedepth-production-green

# shellcheck source=deploy/lib/production-green-target.sh
source "$SOURCE_ROOT/deploy/lib/production-green-target.sh"
# shellcheck source=deploy/lib/production-green-migration.sh
source "$SOURCE_ROOT/deploy/lib/production-green-migration.sh"
load_production_green_target
export BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT

case "$1" in
    prepare)
        production_green_prepare
        ;;
    final-sync)
        if ! production_green_final_sync; then
            production_green_mark_uncertain
            exit 1
        fi
        ;;
    verify)
        production_green_verify
        printf 'Production green data and middleware verification passed.\n'
        ;;
    rollback)
        production_green_require_target
        printf 'Production green rollback preserves its data for manual recovery; the deployment layer must restore Docker traffic.\n'
        ;;
esac
