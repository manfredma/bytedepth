#!/usr/bin/env bash
set -Eeuo pipefail

# One local-only quality entry point for an isolated worktree.
# It prepares this worktree's untracked node_modules before any frontend tool.
# Usage: bash scripts/run-local-quality.sh
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

resolve_java_25() {
    if [[ -x /usr/libexec/java_home ]]; then
        /usr/libexec/java_home -v 25
    elif [[ -n "${JAVA_HOME_25_X64:-}" && -x "$JAVA_HOME_25_X64/bin/java" ]]; then
        printf '%s\n' "$JAVA_HOME_25_X64"
    elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] \
        && "$JAVA_HOME/bin/java" -version 2>&1 | rg -q 'version "25[."]'; then
        printf '%s\n' "$JAVA_HOME"
    else
        printf 'Java 25 is required. Set JAVA_HOME_25_X64 or JAVA_HOME.\n' >&2
        return 1
    fi
}

cd "$SOURCE_ROOT"
npm ci --ignore-scripts --no-audit --no-fund
readonly JAVA_25_HOME="$(resolve_java_25)"
JAVA_HOME="$JAVA_25_HOME" "$SOURCE_ROOT/mvnw" clean install -DskipTests -Dsort.skip=true
JAVA_HOME="$JAVA_25_HOME" "$SOURCE_ROOT/mvnw" test -Dsort.skip=true
npm test
npm run lint
bash scripts/verify-changed-coverage.sh
bash scripts/check-staging-checklist.sh
git diff --check
