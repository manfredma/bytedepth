#!/usr/bin/env bash
set -Eeuo pipefail

# Contract test for the one-command staging checklist.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CHECKLIST="$SOURCE_ROOT/scripts/check-staging-checklist.sh"

[[ -x "$CHECKLIST" ]]
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-deploy-staging.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-run-staging-integration-tests.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-run-staging-e2e-tests.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-prepare-release.sh"' "$CHECKLIST"

printf 'Staging checklist contract passed.\n'
