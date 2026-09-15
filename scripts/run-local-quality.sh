#!/usr/bin/env bash
set -Eeuo pipefail

# One local-only quality entry point for an isolated worktree.
# It prepares this worktree's untracked node_modules before any frontend tool.
# Usage: bash scripts/run-local-quality.sh
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$SOURCE_ROOT/scripts/lib/java-25.sh"

cd "$SOURCE_ROOT"
bash scripts/check-release-readiness.sh
npm ci --ignore-scripts --no-audit --no-fund
readonly JAVA_25_HOME="$(resolve_java_25)"
JAVA_HOME="$JAVA_25_HOME" "$SOURCE_ROOT/mvnw" clean install -DskipTests -Dsort.skip=true
JAVA_HOME="$JAVA_25_HOME" "$SOURCE_ROOT/mvnw" test -Dsort.skip=true
npm test
npm run lint
bash scripts/verify-changed-coverage.sh
bash scripts/check-staging-checklist.sh
git diff --check
