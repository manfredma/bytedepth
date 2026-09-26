#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-e2e-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history
readonly RUNTIME_MANIFEST=/var/lib/bytedepth-staging/runtime/manifest
readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history
readonly LOCK_FILE=/var/lib/bytedepth-staging/deployment-test.lock
readonly TEST_STATE_DIR=/var/lib/bytedepth-staging/test-slots
readonly SLOT_PROVISION=/opt/bytedepth/deploy/provision-staging-test-slot.sh
readonly SLOT_TEARDOWN=/opt/bytedepth/deploy/teardown-staging-test-slot.sh
readonly E2E_BASE_URL=https://staging-bytedepth.bytedepth.cn
readonly E2E_DOMAIN=staging-bytedepth.bytedepth.cn
readonly E2E_LOCAL_RESOLVE="$E2E_DOMAIN:443:127.0.0.1"
readonly E2E_LOCAL_HOST_RESOLVER_RULE="MAP $E2E_DOMAIN 127.0.0.1"
# Shared Chromium is provisioned at the host level by root maintenance.
readonly CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome
source "$SOURCE_ROOT/deploy/lib/staging-runtime.sh"
source "$SOURCE_ROOT/deploy/lib/warning-policy.sh"
source "$SOURCE_ROOT/deploy/lib/staging-test-slot.sh"
source "$SOURCE_ROOT/deploy/lib/staging-native-target.sh"
load_staging_native_target
readonly SLOT_SERVICE="$BYTEDEPTH_STAGING_TEST_SLOT_SERVICE"
if [[ "$BYTEDEPTH_STAGING_RUNTIME_MODE" == host-native-parallel ]]; then
    readonly SLOT_ENV=/run/bytedepth/staging-native-e2e.env
else
    readonly SLOT_ENV=/run/bytedepth/staging-e2e.env
fi
readonly SLOT_RUNTIME_DIR=/run/bytedepth
readonly SLOT_JAR=/opt/bytedepth/current/app.jar
WORK_DIR="$(mktemp -d)"
staging_ensure_ubuntu_owner "$WORK_DIR"
readonly WORK_DIR
readonly E2E_LOG="$WORK_DIR/playwright.log"
readonly SLOT_JOURNAL_LOG="$WORK_DIR/test-slot-journal.log"
touch "$E2E_LOG"
touch "$SLOT_JOURNAL_LOG"
staging_ensure_ubuntu_owner "$E2E_LOG" "$SLOT_JOURNAL_LOG"
manifest=""
run_id=""
tested_commit=""
slot_started_at=""
app_stopped=0
test_slot_started=0
cleanup_done=0
cleanup_result=failed

redact_secrets() {
    local line secret
    local -a secrets=(
        "${BYTEDEPTH_TEST_MEILI_API_KEY:-}"
        "${BYTEDEPTH_STAGING_E2E_PASSWORD:-}"
        "${BYTEDEPTH_STAGING_E2E_DATASOURCE_PASSWORD:-}"
        "${BYTEDEPTH_STAGING_E2E_REDIS_PASSWORD:-}"
    )
    while IFS= read -r line || [[ -n "$line" ]]; do
        for secret in "${secrets[@]}"; do
            [[ -n "$secret" ]] && line="${line//"$secret"/[REDACTED]}"
        done
        printf '%s\n' "$line"
    done
}

load_resource_credentials() {
    local redis_capacity

    for required in \
        BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE \
        BYTEDEPTH_TEST_REDIS_SECRET_FILE \
        BYTEDEPTH_TEST_MEILI_SECRET_FILE \
        BYTEDEPTH_TEST_FIXTURE \
        BYTEDEPTH_TEST_FIXTURE_SHA256_FILE \
        BYTEDEPTH_TEST_STAGING_REDIS_DB \
        BYTEDEPTH_TEST_IT_REDIS_DB \
        BYTEDEPTH_TEST_E2E_REDIS_DB; do
        [[ -n "${!required:-}" ]] || {
            printf 'Refusing: %s must be explicitly injected; no temporary credential is allowed.\n' "$required" >&2
            return 1
        }
    done
    for required in \
        "$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" \
        "$BYTEDEPTH_TEST_REDIS_SECRET_FILE" \
        "$BYTEDEPTH_TEST_MEILI_SECRET_FILE" \
        "$BYTEDEPTH_TEST_FIXTURE" \
        "$BYTEDEPTH_TEST_FIXTURE_SHA256_FILE"; do
        slot_root_private "$required" || return 1
    done
    REDISCLI_AUTH="$(< "$BYTEDEPTH_TEST_REDIS_SECRET_FILE")"
    export REDISCLI_AUTH
    BYTEDEPTH_TEST_MEILI_API_KEY="$(< "$BYTEDEPTH_TEST_MEILI_SECRET_FILE")"
    export BYTEDEPTH_TEST_MEILI_API_KEY
    BYTEDEPTH_TEST_MEILI_URL="${BYTEDEPTH_TEST_MEILI_URL:-http://127.0.0.1:$BYTEDEPTH_STAGING_MEILI_PORT}"
    [[ "$BYTEDEPTH_TEST_MEILI_URL" == "http://127.0.0.1:$BYTEDEPTH_STAGING_MEILI_PORT" ]] || return 1
    export BYTEDEPTH_TEST_MEILI_URL
    redis_capacity="$(slot_redis_cli CONFIG GET databases | tail -n 1)"
    [[ "$redis_capacity" =~ ^[0-9]+$ ]] || return 1
    export BYTEDEPTH_TEST_REDIS_CAPACITY="$redis_capacity"
}

load_manifest_env() {
    local env_file="$1" key value
    [[ -f "$env_file" && ! -L "$env_file" ]] || return 1
    slot_root_private "$env_file" || return 1
    while IFS='=' read -r key value || [[ -n "$key" ]]; do
        [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ && -n "$value" ]] || return 1
        export "$key=$value"
    done < "$env_file"
}

prepare_test_slot_environment() {
    local e2e_env jar sha

    e2e_env="$(slot_manifest_value "$manifest" e2e_env)"
    load_manifest_env "$e2e_env"
    [[ "${SPRING_PROFILES_ACTIVE:-}" == staging-e2e ]] || {
        printf 'Refusing: provisioned E2E environment did not select staging-e2e.\n' >&2
        return 1
    }
    jar="$SLOT_JAR"
    [[ -r "$jar" ]] || {
        printf 'Refusing: deployed test-slot JAR is unavailable.\n' >&2
        return 1
    }
    sha="$(sha256sum "$jar" | awk '{print $1}')"
    [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || return 1
    install -d -o ubuntu -g ubuntu -m 0755 "$SLOT_RUNTIME_DIR"
    install -o ubuntu -g ubuntu -m 0600 "$e2e_env" "$SLOT_ENV"
    printf 'BYTEDEPTH_TEST_SLOT_JAR=%s\nBYTEDEPTH_TEST_SLOT_SHA256=%s\n' "$jar" "$sha" >> "$SLOT_ENV"
}

stop_test_slot() {
    if systemctl is-active --quiet "$SLOT_SERVICE"; then
        systemctl stop "$SLOT_SERVICE" || return 1
    fi
    ! systemctl is-active --quiet "$SLOT_SERVICE"
}

capture_test_slot_journal() {
    [[ -n "$slot_started_at" ]] || return 0
    if ! journalctl --unit="$SLOT_SERVICE" --since="$slot_started_at" --no-pager --output=short-iso 2>&1 \
        | redact_secrets | tee "$SLOT_JOURNAL_LOG"; then
        printf 'Refusing: unable to read the E2E test-slot systemd journal.\n' >&2
        return 1
    fi
    warning_policy_check_file "$SLOT_JOURNAL_LOG" || {
        printf 'Refusing: E2E test-slot journal contains an unallowlisted WARN or WARNING.\n' >&2
        return 1
    }
}

cleanup_slot() {
    local cleanup_status=0

    if (( test_slot_started != 0 )); then
        stop_test_slot || cleanup_status=1
        test_slot_started=0
    fi
    capture_test_slot_journal || cleanup_status=1
    if [[ -n "$manifest" && -f "$manifest" ]]; then
        if [[ -f "$(dirname "$manifest")/state-uncertain" ]]; then
            printf 'Refusing: test resource state is uncertain; preserving manifest and resources.\n' >&2
            cleanup_status=1
        elif ! BYTEDEPTH_TEST_SLOT_LOCK_HELD=1 BYTEDEPTH_DEPLOY_MODE=staging \
            "$SLOT_TEARDOWN" --manifest "$manifest"; then
            cleanup_status=1
        fi
    fi
    if (( app_stopped != 0 )); then
        if ! systemctl is-active --quiet "$BYTEDEPTH_STAGING_APP_SERVICE"; then
            systemctl start "$BYTEDEPTH_STAGING_APP_SERVICE" || cleanup_status=1
        fi
        staging_native_wait_for_http "$BYTEDEPTH_STAGING_APP_SERVICE" \
            "$BYTEDEPTH_STAGING_HEALTH_URL" || cleanup_status=1
        systemctl start "$BYTEDEPTH_STAGING_EDGE_SERVICE" || cleanup_status=1
        staging_native_wait_for_http "$BYTEDEPTH_STAGING_EDGE_SERVICE" \
            "http://127.0.0.1:${BYTEDEPTH_STAGING_EDGE_PORT}" || cleanup_status=1
    fi
    rm -f -- "$SLOT_ENV"
    cleanup_done=1
    if (( cleanup_status == 0 )); then
        cleanup_result=passed
        return 0
    fi
    cleanup_result=failed
    return 1
}

on_exit() {
    local status=$?

    if (( test_slot_started != 0 )); then
        stop_test_slot || status=1
    fi
    if (( cleanup_done == 0 )); then
        cleanup_slot || status=1
    fi
    rm -rf -- "$WORK_DIR"
    exit "$status"
}
trap on_exit EXIT

# Deployment, integration tests and E2E share this lock so an evidence record
# can only be written for a stable deployed checkout.
if [[ "${1:-}" != '--lock-held' ]]; then
    install -d -o ubuntu -g ubuntu -m 0700 "$(dirname "$LOCK_FILE")"
    exec flock -x "$LOCK_FILE" "$0" --lock-held "$@"
fi
shift

read_checked_out_commit() {
    local commit

    commit="$(git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" rev-parse HEAD)"
    if [[ ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
        printf 'Refusing: unable to determine the full checked-out commit SHA.\n' >&2
        exit 1
    fi
    printf '%s\n' "$commit"
}

require_deployed_commit() {
    local expected_commit="$1"
    local deployed_commit

    deployed_commit="$(awk -F= '$1 == "commit" {value = $2} END {print value}' "$DEPLOY_HISTORY" 2>/dev/null || true)"
    if [[ "$deployed_commit" != "$expected_commit" ]]; then
        printf 'Refusing: staging app deployment does not match the tested checkout commit.\n' >&2
        exit 1
    fi
}

invalidate_evidence() {
    rm -f "$EVIDENCE_DIR/staging-e2e"
}

discover_e2e_post_slug() {
    local posts_page

    posts_page="$(curl --fail --silent --show-error --resolve "$E2E_LOCAL_RESOLVE" "$E2E_BASE_URL/posts")"
    if [[ "$posts_page" =~ href=\"/posts/([a-z0-9-]+)\" ]]; then
        printf '%s\n' "${BASH_REMATCH[1]}"
        return
    fi
    printf 'Refusing: staging public article list has no usable post slug for E2E annotation tests.\n' >&2
    exit 1
}

write_evidence() {
    local tested_commit="$1" manifest_sha="$2"
    local evidence_tmp

    if [[ "$(read_checked_out_commit)" != "$tested_commit" ]]; then
        printf 'Refusing: checked-out commit changed during staging E2E tests.\n' >&2
        exit 1
    fi
    require_deployed_commit "$tested_commit"

    install -d -o ubuntu -g ubuntu -m 0700 "$EVIDENCE_DIR"
    evidence_tmp="$(mktemp "$EVIDENCE_DIR/.staging-e2e.XXXXXX")"
    printf 'commit=%s\ncommand=run-staging-e2e-tests\ntimestamp=%s\nresult=passed\nruntime_mode=host-native\nrun_id=%s\ntest_resource_manifest_sha=%s\ncleanup=result=passed\n' \
        "$tested_commit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$run_id" "$manifest_sha" > "$evidence_tmp"
    install -o ubuntu -g ubuntu -m 0600 "$evidence_tmp" "$EVIDENCE_DIR/staging-e2e"
    rm -f "$evidence_tmp"
}

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = substr($0, index($0, "=") + 1)} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != 'staging' ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging, got %s\n' "${deploy_mode:-unset}" >&2
    exit 1
fi

if [[ -z "${BYTEDEPTH_STAGING_E2E_USERNAME:-}" || -z "${BYTEDEPTH_STAGING_E2E_PASSWORD:-}" ]]; then
    printf 'Refusing: BYTEDEPTH_STAGING_E2E_USERNAME and BYTEDEPTH_STAGING_E2E_PASSWORD are required for annotation content-update E2E.\n' >&2
    exit 1
fi

invalidate_evidence
tested_commit="$(read_checked_out_commit)"
require_deployed_commit "$tested_commit"
require_staging_runtime "$RUNTIME_MANIFEST" "$SOURCE_ROOT"
load_resource_credentials

if [[ ! -x "$CHROMIUM_EXECUTABLE" ]]; then
    printf 'Refusing: staging Chromium executable is unavailable at %s\n' "$CHROMIUM_EXECUTABLE" >&2
    exit 1
fi

cd "$SOURCE_ROOT"
run_id="$(date -u +%Y%m%d_%H%M%S)_$(openssl rand -hex 4)"
manifest="$TEST_STATE_DIR/$run_id/manifest"
export BYTEDEPTH_TEST_STATE_DIR="$TEST_STATE_DIR"
export BYTEDEPTH_TEST_CANDIDATE_SHA="$tested_commit"
export BYTEDEPTH_TEST_SLOT_LOCK_HELD=1
export BYTEDEPTH_DEPLOY_MODE=staging
install -d -o ubuntu -g ubuntu -m 0700 "$TEST_STATE_DIR"
systemctl stop "$BYTEDEPTH_STAGING_APP_SERVICE"
app_stopped=1
if systemctl is-active --quiet "$BYTEDEPTH_STAGING_APP_SERVICE"; then
    printf 'Refusing: staging app remained active; E2E resources were not provisioned.\n' >&2
    exit 1
fi
"$SLOT_PROVISION" --run-id "$run_id" --manifest "$manifest"
require_manifest "$manifest"
export BYTEDEPTH_TEST_MANIFEST="$manifest"
manifest_sha="$(shasum -a 256 "$manifest" | awk '{print $1}')"
[[ "$manifest_sha" =~ ^[0-9a-f]{64}$ ]] || {
    printf 'Refusing: unable to digest the E2E resource manifest.\n' >&2
    exit 1
}
prepare_test_slot_environment
slot_started_at="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
systemctl start "$SLOT_SERVICE"
test_slot_started=1
for attempt in {1..30}; do
    if systemctl is-active --quiet "$SLOT_SERVICE" && \
        curl --fail --silent --show-error --connect-timeout 3 --max-time 10 \
            --resolve "$E2E_LOCAL_RESOLVE" "$E2E_BASE_URL/version" >/dev/null; then
        break
    fi
    if [[ "$attempt" == 30 ]]; then
        printf 'Refusing: native E2E test slot did not become healthy.\n' >&2
        exit 1
    fi
    sleep 1
done
e2e_post_slug="$(discover_e2e_post_slug)"
export E2E_BASE_URL
export E2E_LOCAL_HOST_RESOLVER_RULE
if ! E2E_POST_SLUG="$e2e_post_slug" \
    E2E_ADMIN_USERNAME="$BYTEDEPTH_STAGING_E2E_USERNAME" \
    E2E_ADMIN_PASSWORD="$BYTEDEPTH_STAGING_E2E_PASSWORD" \
    PLAYWRIGHT_CHROMIUM_EXECUTABLE="$CHROMIUM_EXECUTABLE" \
    npm run test:e2e 2>&1 | tee "$E2E_LOG"; then
    printf 'Staging E2E tests failed.\n' >&2
    exit 1
fi

if ! warning_policy_check_file "$E2E_LOG"; then
    printf 'Refusing: Playwright output contains an unallowlisted WARN or WARNING.\n' >&2
    exit 1
fi

stop_test_slot
test_slot_started=0
cleanup_slot
[[ "$cleanup_result" == passed ]] || {
    printf 'Refusing: E2E cleanup, staging restoration, or test-slot warning check failed.\n' >&2
    exit 1
}
write_evidence "$tested_commit" "$manifest_sha"
printf 'Staging E2E tests passed.\n'
