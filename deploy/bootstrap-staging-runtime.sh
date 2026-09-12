#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/bootstrap-staging-runtime.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly LOCK_FILE="$STATE_DIR/deployment-test.lock"
readonly RUNTIME_MANIFEST="$STATE_DIR/runtime/manifest"
source "$SOURCE_ROOT/deploy/lib/timing.sh"
source "$SOURCE_ROOT/deploy/lib/staging-runtime.sh"

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = $2} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != staging ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging.\n' >&2
    exit 1
fi

install -d -o root -g root -m 0700 "$STATE_DIR"
exec 9>"$LOCK_FILE"
flock -x 9

commit="$(git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" rev-parse HEAD)"
timing_file="$STATE_DIR/runtime/timing/$commit"
initialize_timing_file "$timing_file" "$commit"
bootstrap_started_at="$(timing_now_epoch_ms)"

prepare_maven() {
    cd "$SOURCE_ROOT"
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")" \
        mvn clean install -DskipTests -Dsort.skip=true
}

prepare_node() {
    cd "$SOURCE_ROOT"
    npm ci
    PLAYWRIGHT_BROWSERS_PATH="$SOURCE_ROOT/.e2e" npx playwright install chromium
}

if ! record_timed_phase "$timing_file" maven_runtime_prepare prepare_maven; then
    record_timing_phase "$timing_file" bootstrap_total failed "$(timing_now_epoch_ms)" "$(timing_now_epoch_ms)"
    exit 1
fi
if ! record_timed_phase "$timing_file" node_runtime_prepare prepare_node; then
    record_timing_phase "$timing_file" bootstrap_total failed "$(timing_now_epoch_ms)" "$(timing_now_epoch_ms)"
    exit 1
fi
write_runtime_manifest "$RUNTIME_MANIFEST" "$SOURCE_ROOT" "$commit"
record_timing_phase "$timing_file" bootstrap_total passed "$bootstrap_started_at" "$(timing_now_epoch_ms)"
printf 'Staging runtime bootstrap completed for %s.\n' "$commit"
