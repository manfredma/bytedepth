#!/usr/bin/env bash
set -Eeuo pipefail

# One local-only quality entry point for an isolated worktree.
# It prepares this worktree's untracked node_modules before any frontend tool.
# Usage: bash scripts/run-local-quality.sh
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$SOURCE_ROOT/scripts/lib/java-25.sh"

cd "$SOURCE_ROOT"
bash scripts/check-release-readiness.sh --target HEAD --base origin/main --mode candidate
npm ci --ignore-scripts --no-audit --no-fund
bash scripts/format-check.sh --all
bash scripts/test-format-code.sh
bash scripts/test-agent-delivery-workflow.sh
bash scripts/test-handoff-documentation.sh
bash scripts/test-staging-platform-contract.sh
bash scripts/test-staging-runtime-preparation.sh
bash scripts/test-staging-test-config.sh
bash scripts/test-run-staging-integration-tests.sh
bash scripts/test-run-staging-e2e-tests.sh
bash scripts/test-annotation-unit-evidence.sh
rm -rf target/annotation-unit-evidence coverage
readonly ANNOTATION_RUN_DIR="$SOURCE_ROOT/.superpowers/quality/annotation-unit"
python3 scripts/verify-annotation-unit-evidence.py --begin "$ANNOTATION_RUN_DIR/run.json"
readonly JAVA_25_HOME="$(resolve_java_25)"
JAVA_HOME="$JAVA_25_HOME" "$SOURCE_ROOT/mvnw" clean install -DskipTests -Dsort.skip=true
JAVA_HOME="$JAVA_25_HOME" "$SOURCE_ROOT/mvnw" test -Dsort.skip=true
bash scripts/verify-changed-coverage.sh
npm test
npm run lint
python3 scripts/verify-annotation-unit-evidence.py \
  --vitest target/annotation-unit-evidence/vitest.json \
  --surefire-root "$SOURCE_ROOT" --coverage coverage/coverage-final.json \
  --cases scripts/test-fixtures/annotation/unit-cases.json \
  --run-context "$ANNOTATION_RUN_DIR/run.json" --seal "$ANNOTATION_RUN_DIR/seal.json" --record-seal
python3 scripts/test-annotation-mutations.py
git diff --check
