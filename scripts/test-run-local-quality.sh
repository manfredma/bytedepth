#!/usr/bin/env bash
set -Eeuo pipefail

# Static contract for the local-only quality entrypoint. It verifies ordering
# and required preflight commands without running the full quality suite.

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RUNNER="$SOURCE_ROOT/scripts/run-local-quality.sh"

[[ -x "$RUNNER" ]]
grep -Fqx 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER"
grep -Fq 'bash scripts/format-check.sh --all' "$RUNNER"
grep -Fq 'bash scripts/test-format-code.sh' "$RUNNER"
grep -Fqx 'npm test' "$RUNNER"
grep -Fqx 'npm run lint' "$RUNNER"
grep -Fqx 'git diff --check' "$RUNNER"
grep -Fqx 'source "$SOURCE_ROOT/scripts/lib/java-25.sh"' "$RUNNER"
grep -Fqx 'resolve_java_25() {' "$SOURCE_ROOT/scripts/lib/java-25.sh"
[[ "$(grep -nF 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]
[[ "$(grep -nF 'bash scripts/format-check.sh --all' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]
[[ "$(grep -nF 'bash scripts/test-format-code.sh' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]
readiness_line="$(grep -nF 'bash scripts/check-release-readiness.sh' "$RUNNER" | cut -d: -f1)"
npm_ci_line="$(grep -nF 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER" | cut -d: -f1)"
[[ -n "$readiness_line" && -n "$npm_ci_line" && "$readiness_line" -lt "$npm_ci_line" ]]
! rg -qi 'ssh|staging|production|deploy|release-platform-agent' "$RUNNER"

printf 'Local frontend dependency preflight contract passed.\n'
