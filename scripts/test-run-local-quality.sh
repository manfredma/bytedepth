#!/usr/bin/env bash
set -Eeuo pipefail

# Static contract for the local-only quality entrypoint. It verifies ordering
# and required preflight commands without running the full quality suite.

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RUNNER="$SOURCE_ROOT/scripts/run-local-quality.sh"

[[ -x "$RUNNER" ]]
grep -Fqx 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER"
grep -Fq 'bash scripts/format-check.sh --all' "$RUNNER"
grep -Fqx 'bash scripts/test-warning-policy.sh' "$RUNNER"
grep -Fq 'bash scripts/test-format-code.sh' "$RUNNER"
grep -Fqx 'bash scripts/test-agent-delivery-workflow.sh' "$RUNNER"
grep -Fqx 'bash scripts/test-handoff-documentation.sh' "$RUNNER"
grep -Fqx 'npm test' "$RUNNER"
grep -Fqx 'npm run lint' "$RUNNER"
grep -Fqx 'bash scripts/run-mobile-quality.sh' "$RUNNER"
grep -Fqx 'bash scripts/test-run-mobile-quality.sh' "$RUNNER"
grep -Fqx 'node --test scripts/test-fixtures/mobile/*.test.mjs' "$RUNNER"
grep -Fqx 'git diff --check' "$RUNNER"
grep -Fqx 'source "$SOURCE_ROOT/scripts/lib/java-25.sh"' "$RUNNER"
grep -Fqx 'resolve_java_25() {' "$SOURCE_ROOT/scripts/lib/java-25.sh"
[[ "$(grep -nF 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]
[[ "$(grep -nF 'bash scripts/format-check.sh --all' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]
[[ "$(grep -nF 'bash scripts/test-format-code.sh' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]
readiness_line="$(grep -nF 'bash scripts/check-release-readiness.sh' "$RUNNER" | cut -d: -f1)"
npm_ci_line="$(grep -nF 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER" | cut -d: -f1)"
[[ -n "$readiness_line" && -n "$npm_ci_line" && "$readiness_line" -lt "$npm_ci_line" ]]
# Static staging contract checks are local tests, not external execution.
# An inverted command is exempt from set -e; use an explicit failing branch.
if rg -ni '(^|[[:space:]])(ssh|scp|sftp|curl|wget|sudo)[[:space:]]|deploy/|-Pstaging-integration|npm run test:e2e|npx playwright test|release-platform-agent' "$RUNNER"; then
  printf 'Local quality runner must not execute external service or deployment commands.\n' >&2
  exit 1
fi

printf 'Local frontend dependency preflight contract passed.\n'
