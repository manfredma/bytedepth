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
source "$SOURCE_ROOT/deploy/lib/warning-policy.sh"

if [[ "${1:-}" != --lock-held ]]; then
    install -d -o root -g root -m 0700 "$STATE_DIR"
    exec flock -x "$LOCK_FILE" "$0" --lock-held "$@"
fi
shift

case "${1:-}" in
    '')
        bootstrap_mode=refresh
        ;;
    --ensure)
        bootstrap_mode=ensure
        ;;
    *)
        printf 'Usage: sudo ./deploy/bootstrap-staging-runtime.sh [--ensure]\n' >&2
        exit 1
        ;;
esac

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = $2} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != staging ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging.\n' >&2
    exit 1
fi

commit="$(git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" rev-parse HEAD)"
if [[ "$bootstrap_mode" == ensure && -d "$SHARED_MAVEN_REPOSITORY" ]] \
    && require_staging_runtime "$RUNTIME_MANIFEST" "$SOURCE_ROOT" >/dev/null 2>&1; then
    printf 'Staging runtime already satisfies the current dependency inputs.\n'
    exit 0
fi
timing_file="$STATE_DIR/runtime/timing/$commit"
initialize_timing_file "$timing_file" "$commit"
bootstrap_started_at="$(timing_now_epoch_ms)"

prepare_maven() {
    install -d -o root -g root -m 0755 "$SHARED_MAVEN_REPOSITORY"
    (
        flock -x 8
        cd "$SOURCE_ROOT"
        maven_log="$(mktemp)"
        trap 'rm -f "$maven_log"' EXIT
        set +e
        (
            set -Eeuo pipefail
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" clean install -DskipTests -Dsort.skip=true
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" \
                dependency:go-offline -Dsort.skip=true -DincludePlugins=true -DincludePluginDependencies=true -DskipTests
            # Surefire/Failsafe select their JUnit runtime dynamically, outside the
            # dependency graph visible to dependency:go-offline.  Resolve that exact
            # runtime without running a test: the impossible selector and the two
            # fail-if-no-match flags keep this a dependency probe rather than test execution.
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" \
                -Pstaging-integration verify -Dtest=staging_bootstrap_dependency_probe \
                -Dit.test=staging_bootstrap_dependency_probe -Dsurefire.failIfNoSpecifiedTests=false \
                -Dfailsafe.failIfNoSpecifiedTests=false -DskipTests=false -Dsort.skip=true
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" \
                -o -Pstaging-integration verify -Dtest=staging_bootstrap_dependency_probe \
                -Dit.test=staging_bootstrap_dependency_probe -Dsurefire.failIfNoSpecifiedTests=false \
                -Dfailsafe.failIfNoSpecifiedTests=false -DskipTests=false -Dsort.skip=true
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" verify -DskipTests -Dsort.skip=true
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" -Pstaging-integration verify -DskipTests -Dsort.skip=true
            "$SOURCE_ROOT/mvnw" -s .mvn/settings.xml -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" -o -Pstaging-integration verify -DskipTests -Dsort.skip=true
        ) 2>&1 | tee "$maven_log"
        maven_status="${PIPESTATUS[0]}"
        set -e
        if [[ "$maven_status" -ne 0 ]]; then
            return "$maven_status"
        fi
        if ! warning_policy_check_file "$maven_log"; then
            printf 'Refusing: Maven runtime preparation emitted an unallowlisted WARN or WARNING.\n' >&2
            return 1
        fi
    ) 8>"$SHARED_MAVEN_LOCK"
}

prepare_node() {
    cd "$SOURCE_ROOT"
    npm ci --ignore-scripts --no-audit --no-fund
}

if ! record_timed_phase "$timing_file" maven_runtime_prepare prepare_maven; then
    record_timing_phase "$timing_file" bootstrap_total failed "$(timing_now_epoch_ms)" "$(timing_now_epoch_ms)"
    exit 1
fi
if ! record_timed_phase "$timing_file" node_runtime_prepare prepare_node; then
    record_timing_phase "$timing_file" bootstrap_total failed "$(timing_now_epoch_ms)" "$(timing_now_epoch_ms)"
    exit 1
fi
write_runtime_manifest "$RUNTIME_MANIFEST" "$SOURCE_ROOT"
record_timing_phase "$timing_file" bootstrap_total passed "$bootstrap_started_at" "$(timing_now_epoch_ms)"
printf 'Staging runtime bootstrap completed for %s.\n' "$commit"
