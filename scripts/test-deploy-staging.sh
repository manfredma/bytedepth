#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/deploy/deploy-staging.sh"

require_line() {
    rg -F "$1" "$SCRIPT" >/dev/null || {
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
require_line 'require_staging_runtime'

if rg -q 'bootstrap-staging-runtime\.sh|npm ci|playwright install|docker run' "$SCRIPT"; then
    printf 'Staging deployment must consume, not repair, runtime or create test containers.\n' >&2
    exit 1
fi

printf 'Staging deployment contract passed.\n'
