#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/deploy/deploy-staging.sh"

require_line() {
    rg -F -- "$1" "$SCRIPT" >/dev/null || {
        printf 'Missing staging deployment contract: %s\n' "$1" >&2
        exit 1
    }
}

require_line 'readonly STATE_DIR=/var/lib/bytedepth-staging'
require_line 'deployment-test.lock'
require_line 'staging-integration'
require_line 'staging-e2e'
require_line 'initialize_timing_file "$TIMING_FILE" "$COMMIT"'
require_line 'record_timed_phase "$TIMING_FILE" source_checkout'
require_line 'record_timed_phase "$TIMING_FILE" runtime_preflight'
require_line 'record_timed_phase "$TIMING_FILE" docker_build_and_rollout'
require_line 'record_timing_phase "$TIMING_FILE" deployment_total passed'
require_line 'record_timing_phase "$TIMING_FILE" deployment_total failed'
require_line 'require_staging_runtime_prerequisites'
require_line 'staging-bytedepth.bytedepth.cn'
require_line 'BYTEDEPTH_SITE_URL'
require_line 'staging TLS certificate'
require_line 'require_staging_host_configuration'
require_line '-ext subjectAltName'
require_line 'DNS:$EXPECTED_STAGING_DOMAIN'
require_line './deploy/bootstrap-staging-runtime.sh --lock-held --ensure'
require_line 'BYTEDEPTH_STAGING_HOST:-124.221.143.25'
require_line 'sudo ./deploy/deploy-staging.sh $1'
require_line 'git_cmd fetch --force --no-recurse-submodules origin "$REF" main'
require_line 'bash scripts/check-staging-changelog-change.sh --target "$COMMIT" --base origin/main'
require_line 'bash scripts/check-release-readiness.sh --target "$COMMIT" --base origin/main --mode candidate'

changelog_gate_line="$(rg -nF 'bash scripts/check-staging-changelog-change.sh --target "$COMMIT" --base origin/main' "$SCRIPT" | cut -d: -f1)"
readiness_line="$(rg -nF 'bash scripts/check-release-readiness.sh --target "$COMMIT" --base origin/main --mode candidate' "$SCRIPT" | cut -d: -f1)"
preflight_line="$(rg -nF 'record_timed_phase "$TIMING_FILE" runtime_preflight' "$SCRIPT" | cut -d: -f1)"
rollout_line="$(rg -nF './deploy/bootstrap-ops-deploy.sh' "$SCRIPT" | cut -d: -f1)"
runtime_bootstrap_line="$(rg -nF './deploy/bootstrap-staging-runtime.sh --lock-held --ensure' "$SCRIPT" | cut -d: -f1)"
[[ "$readiness_line" -lt "$preflight_line" ]]
[[ "$readiness_line" -lt "$runtime_bootstrap_line" ]]
[[ "$readiness_line" -lt "$rollout_line" ]]
[[ "$changelog_gate_line" -lt "$readiness_line" ]]

if rg -q 'require_staging_runtime "\$STATE_DIR/runtime/manifest"' "$SCRIPT"; then
    printf 'Deployment must not require a checkout-bound manifest before bootstrap can create it.\n' >&2
    exit 1
fi

if rg -q 'npm ci|playwright install|docker run' "$SCRIPT"; then
    printf 'Staging deployment must not create test runtimes or containers directly.\n' >&2
    exit 1
fi

printf 'Staging deployment contract passed.\n'
