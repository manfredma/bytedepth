#!/usr/bin/env bash
set -Eeuo pipefail

# One local-only quality entry point for an isolated worktree.
# It prepares this worktree's untracked node_modules before any frontend tool.
# Usage: bash scripts/run-local-quality.sh
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cd "$SOURCE_ROOT"
npm ci --ignore-scripts --no-audit --no-fund
JAVA_HOME="$(/usr/libexec/java_home -v 25)" "$SOURCE_ROOT/mvnw" clean install -DskipTests -Dsort.skip=true
JAVA_HOME="$(/usr/libexec/java_home -v 25)" "$SOURCE_ROOT/mvnw" test -Dsort.skip=true
npm test
npm run lint
bash scripts/verify-changed-coverage.sh
