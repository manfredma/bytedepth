#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/deploy-production.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable production deploy script.\n' >&2; exit 1; }
for contract in \
    'must be an annotated tag' \
    'was already deployed on this node' \
    '--artifact JAR --manifest MANIFEST' \
    'validate_artifact_manifest' \
    'install_release_artifact' \
    'switch_current_release' \
    'systemctl restart bytedepth-app.service' \
    'verify_running_release' \
    'systemctl reload nginx.service'; do
    rg -F -- "$contract" "$SCRIPT" >/dev/null || {
        printf 'Missing production deployment contract: %s\n' "$contract" >&2
        exit 1
    }
done
if rg -n -i 'docker|compose|prewarm-production-maven-cache|mvn ' "$SCRIPT" >/dev/null; then
    printf 'Production host deployment must not build with Docker or Maven.\n' >&2
    exit 1
fi
grep -Fq 'This is a production-host-only script' "$SCRIPT"
grep -Fq 'deploy-production-remote.sh' "$SCRIPT"

printf 'Production deployment contract passed.\n'
