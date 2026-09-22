#!/usr/bin/env bash
set -Eeuo pipefail

# Executes every cheap, reproducible staging contract check before a release.
# Usage: bash scripts/check-staging-checklist.sh
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

bash "$SOURCE_ROOT/scripts/test-changelog-order.sh"
bash "$SOURCE_ROOT/scripts/test-release-sequence.sh"
bash "$SOURCE_ROOT/scripts/test-check-release-readiness.sh"
bash "$SOURCE_ROOT/scripts/test-check-staging-changelog-change.sh"
bash "$SOURCE_ROOT/scripts/test-run-local-quality.sh"
bash "$SOURCE_ROOT/scripts/test-github-quality-workflow.sh"
bash "$SOURCE_ROOT/scripts/test-maven-runtime.sh"
bash "$SOURCE_ROOT/scripts/test-warning-policy.sh"
bash "$SOURCE_ROOT/scripts/test-platform-portability.sh"
bash "$SOURCE_ROOT/scripts/test-flyway-migration-warning-safety.sh"
bash "$SOURCE_ROOT/scripts/test-deploy-staging.sh"
bash "$SOURCE_ROOT/scripts/test-run-staging-integration-tests.sh"
bash "$SOURCE_ROOT/scripts/test-run-staging-e2e-tests.sh"
bash "$SOURCE_ROOT/scripts/test-staging-e2e-credential-injection.sh"
bash "$SOURCE_ROOT/scripts/test-staging-preview-route.sh"
bash "$SOURCE_ROOT/scripts/test-staging-search-isolation.sh"
bash "$SOURCE_ROOT/scripts/test-sync-staging-certificate.sh"
bash "$SOURCE_ROOT/scripts/test-prepare-release.sh"
bash "$SOURCE_ROOT/scripts/test-deploy-production.sh"
bash "$SOURCE_ROOT/scripts/test-deploy-production-remote.sh"
bash "$SOURCE_ROOT/scripts/test-verify-production-release.sh"

printf 'Staging checklist passed.\n'
