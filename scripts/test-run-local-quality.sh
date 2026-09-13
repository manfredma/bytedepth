#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RUNNER="$SOURCE_ROOT/scripts/run-local-quality.sh"

[[ -x "$RUNNER" ]]
grep -Fqx 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER"
grep -Fqx 'npm test' "$RUNNER"
grep -Fqx 'npm run lint' "$RUNNER"
grep -Fqx 'bash scripts/check-staging-checklist.sh' "$RUNNER"
grep -Fqx 'git diff --check' "$RUNNER"
[[ "$(grep -nF 'npm ci --ignore-scripts --no-audit --no-fund' "$RUNNER" | cut -d: -f1)" -lt "$(grep -nF 'npm test' "$RUNNER" | cut -d: -f1)" ]]

printf 'Local frontend dependency preflight contract passed.\n'
