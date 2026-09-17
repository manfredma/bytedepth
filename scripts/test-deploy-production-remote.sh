#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/deploy/deploy-production-remote.sh"
readonly HOST_SCRIPT="$ROOT/deploy/deploy-production.sh"

[[ -x "$SCRIPT" ]] || {
    printf 'Expected executable local production deployment wrapper.\n' >&2
    exit 1
}
grep -Fqx 'readonly PRODUCTION_HOST=175.24.197.202' "$SCRIPT"
grep -Fqx 'readonly REMOTE_ROOT=/opt/bytedepth' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KEY' "$SCRIPT"
grep -Fq 'IdentitiesOnly=yes' "$SCRIPT"
grep -Fq 'BatchMode=yes' "$SCRIPT"
grep -Fq 'deploy-production.sh' "$SCRIPT"
grep -Fq 'nohup' "$SCRIPT"
grep -Fq 'verify-production-release.sh' "$SCRIPT"
grep -Fq 'release-history' "$SCRIPT"
grep -Fq 'WARNING' "$SCRIPT"
grep -Fq 'This is a production-host-only script' "$HOST_SCRIPT"
grep -Fq 'deploy-production-remote.sh' "$HOST_SCRIPT"
if grep -Fq 'Run this script with sudo: sudo ./deploy/deploy-production.sh' "$HOST_SCRIPT"; then
    printf 'Host-only production script must not suggest local sudo execution.\n' >&2
    exit 1
fi

printf 'Local production deployment contract passed.\n'
