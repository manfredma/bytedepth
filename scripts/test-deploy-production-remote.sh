#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/deploy-production-remote.sh"
readonly HOST_SCRIPT="$ROOT/deploy/deploy-production.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable local production deployment wrapper.\n' >&2; exit 1; }
grep -Fqx 'readonly PRODUCTION_HOST=175.24.197.202' "$SCRIPT"
grep -Fqx 'readonly REMOTE_ROOT=/opt/bytedepth' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KEY' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS' "$SCRIPT"
grep -Fq 'UserKnownHostsFile=' "$SCRIPT"
grep -Fq 'StrictHostKeyChecking=yes' "$SCRIPT"
grep -Fq 'scp ' "$SCRIPT"
grep -Fq -- '--artifact' "$SCRIPT"
grep -Fq -- '--manifest' "$SCRIPT"
grep -Fq 'verify-production-release.sh' "$SCRIPT"
grep -Fq 'release-history' "$SCRIPT"
grep -Fq 'WARNING' "$SCRIPT"
if rg -n -i 'docker|compose|mvn ' "$SCRIPT" "$HOST_SCRIPT" >/dev/null; then
    printf 'Production deployment path must not invoke Docker, Compose, or target-host Maven.\n' >&2
    exit 1
fi

printf 'Local production deployment contract passed.\n'
