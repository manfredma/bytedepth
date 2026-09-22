#!/usr/bin/env bash
set -Eeuo pipefail

# Contract test for the one-command staging checklist.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CHECKLIST="$SOURCE_ROOT/scripts/check-staging-checklist.sh"

[[ -x "$CHECKLIST" ]]
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-changelog-order.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-release-sequence.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-run-local-quality.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-check-release-readiness.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-check-staging-changelog-change.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-github-quality-workflow.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-maven-runtime.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-platform-portability.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-flyway-migration-warning-safety.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-deploy-staging.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-run-staging-integration-tests.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-run-staging-e2e-tests.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-staging-e2e-credential-injection.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-staging-preview-route.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-staging-search-isolation.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-prepare-release.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-deploy-production.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-deploy-production-remote.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-verify-production-release.sh"' "$CHECKLIST"

grep -Fq 'scripts/check-release-readiness.sh' "$SOURCE_ROOT/scripts/run-local-quality.sh"
grep -Fq 'scripts/check-release-readiness.sh --target "$COMMIT" --base origin/main --mode candidate' "$SOURCE_ROOT/deploy/deploy-staging.sh"
grep -Fq 'scripts/check-release-readiness.sh --target "$SHA" --base origin/main --mode candidate' "$SOURCE_ROOT/scripts/merge-main-after-quality.sh"
grep -Fq 'scripts/check-staging-changelog-change.sh --target "$SHA" --base origin/main' "$SOURCE_ROOT/scripts/merge-main-after-quality.sh"
grep -Fq 'scripts/check-release-readiness.sh --target HEAD --base origin/main --mode release' "$SOURCE_ROOT/scripts/prepare-release.sh"

for process_doc in \
    "$SOURCE_ROOT/AGENTS.md" \
    "$SOURCE_ROOT/docs/releases/README.md" \
    "$SOURCE_ROOT/docs/engineering/git-workflow.md" \
    "$SOURCE_ROOT/docs/engineering/unified-release-pipeline.md"; do
    grep -Fq '首次 staging 部署前' "$process_doc"
    grep -Fq 'Unreleased' "$process_doc"
done

grep -Fq 'deploy-production-remote.sh' "$SOURCE_ROOT/deploy/README.md"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KEY' "$SOURCE_ROOT/docs/releases/README.md"
if grep -Fq '生产打新 SemVer Tag，部署到 175（生产单机）：`deploy/deploy-production.sh vTag`' \
    "$SOURCE_ROOT/deploy/README.md" "$SOURCE_ROOT/docs/releases/README.md" ||
    grep -Fq '9. `deploy/deploy-production.sh <tag>` 部署该新 Tag。' \
        "$SOURCE_ROOT/docs/engineering/unified-release-pipeline.md" ||
    grep -Fq '14. `deploy/deploy-production.sh <tag>` 部署该 Tag' \
        "$SOURCE_ROOT/docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md"; then
    printf 'Current production instructions must use deploy-production-remote.sh as the local entry.\n' >&2
    exit 1
fi

printf 'Staging checklist contract passed.\n'
