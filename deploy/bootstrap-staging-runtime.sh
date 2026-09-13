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

if [[ "${1:-}" != --lock-held ]]; then
    install -d -o root -g root -m 0700 "$STATE_DIR"
    exec flock -x "$LOCK_FILE" "$0" --lock-held "$@"
fi
shift

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = $2} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != staging ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging.\n' >&2
    exit 1
fi

commit="$(git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" rev-parse HEAD)"
timing_file="$STATE_DIR/runtime/timing/$commit"
initialize_timing_file "$timing_file" "$commit"
bootstrap_started_at="$(timing_now_epoch_ms)"

prepare_maven() {
    install -d -o root -g root -m 0755 "$SHARED_MAVEN_REPOSITORY"
    (
        flock -x 8
        cd "$SOURCE_ROOT"
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")" \
            mvn -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" clean install -DskipTests -Dsort.skip=true
    ) 8>"$SHARED_MAVEN_LOCK"
}

prepare_node() {
    cd "$SOURCE_ROOT"
    npm ci
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
