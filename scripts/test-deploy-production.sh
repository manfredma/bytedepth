#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/deploy/deploy-production.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable production deploy script.\n' >&2; exit 1; }
rg -F 'must be an annotated tag' "$SCRIPT" >/dev/null
rg -F 'tag %s and Maven version' "$SCRIPT" >/dev/null
rg -F 'was already deployed on this node' "$SCRIPT" >/dev/null
rg -F './deploy/bootstrap-ops-deploy.sh' "$SCRIPT" >/dev/null
rg -F './deploy/prewarm-production-maven-cache.sh "$SOURCE_ROOT"' "$SCRIPT" >/dev/null
test -x "$ROOT/deploy/prewarm-production-maven-cache.sh"
rg -F 'maven:3.9.11-eclipse-temurin-25' "$ROOT/deploy/prewarm-production-maven-cache.sh" >/dev/null
if [[ -e "$ROOT/deploy/deploy-release.sh" ]]; then
    printf 'Legacy deploy-release.sh must not remain after production script standardization.\n' >&2
    exit 1
fi

printf 'Production deployment contract passed.\n'
