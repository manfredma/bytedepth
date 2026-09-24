#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-integration-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly EVIDENCE_DIR="$STATE_DIR/test-history"
readonly DEPLOY_HISTORY="$STATE_DIR/deploy-history"
readonly LOCK_FILE="$STATE_DIR/deployment-test.lock"
readonly TEST_STATE_DIR="$STATE_DIR/test-slots"
readonly RUNTIME_MANIFEST="$STATE_DIR/runtime/manifest"
readonly SHARED_MAVEN_REPOSITORY=/opt/shared-maven/repository
readonly MINIMUM_WORKSPACE_FREE_KIB=2097152
readonly SLOT_PROVISION="$SOURCE_ROOT/deploy/provision-staging-test-slot.sh"
readonly SLOT_TEARDOWN="$SOURCE_ROOT/deploy/teardown-staging-test-slot.sh"
# shellcheck disable=SC1091
source "$SOURCE_ROOT/deploy/lib/staging-runtime.sh"
# shellcheck disable=SC1091
source "$SOURCE_ROOT/deploy/lib/warning-policy.sh"
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/staging-test-slot.sh"

WORK_DIR=""
MAVEN_LOG=""
tested_commit=""
manifest=""
run_id=""
app_stopped=0
cleanup_done=0
cleanup_result=failed

read_checked_out_commit() {
    local commit

    commit="$(git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" rev-parse HEAD)"
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || {
        printf 'Refusing: unable to determine the full checked-out commit SHA.\n' >&2
        return 1
    }
    printf '%s\n' "$commit"
}

require_deployed_commit() {
    local expected_commit="$1"
    local deployed_commit

    deployed_commit="$(awk -F= '$1 == "commit" {value = $2} END {print value}' "$DEPLOY_HISTORY" 2>/dev/null || true)"
    [[ "$deployed_commit" == "$expected_commit" ]] || {
        printf 'Refusing: staging app deployment does not match the tested checkout commit.\n' >&2
        return 1
    }
}

invalidate_evidence() {
    install -d -o root -g root -m 0700 "$EVIDENCE_DIR"
    rm -f -- "$EVIDENCE_DIR/staging-integration"
}

require_workspace_headroom() {
    local available_kib

    available_kib="$(df -Pk "$SOURCE_ROOT" | awk 'NR == 2 {print $4}')"
    [[ "$available_kib" =~ ^[0-9]+$ && "$available_kib" -ge "$MINIMUM_WORKSPACE_FREE_KIB" ]] || {
        printf 'Refusing: staging workspace needs at least %s KiB free, found %s KiB.\n' \
            "$MINIMUM_WORKSPACE_FREE_KIB" "${available_kib:-unknown}" >&2
        return 1
    }
}

require_test_slot_inputs() {
    local required

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
            printf 'Refusing: %s must be explicitly injected; no default or temporary credential is allowed.\n' "$required" >&2
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
}

load_resource_credentials() {
    local redis_capacity

    REDISCLI_AUTH="$(< "$BYTEDEPTH_TEST_REDIS_SECRET_FILE")"
    export REDISCLI_AUTH
    BYTEDEPTH_TEST_MEILI_API_KEY="$(< "$BYTEDEPTH_TEST_MEILI_SECRET_FILE")"
    export BYTEDEPTH_TEST_MEILI_API_KEY
    BYTEDEPTH_TEST_MEILI_URL="${BYTEDEPTH_TEST_MEILI_URL:-http://127.0.0.1:7700}"
    [[ "$BYTEDEPTH_TEST_MEILI_URL" == http://127.0.0.1:7700 ]] || {
        printf 'Refusing: Meili must use the local staging endpoint.\n' >&2
        return 1
    }
    export BYTEDEPTH_TEST_MEILI_URL
    redis_capacity="$(redis-cli CONFIG GET databases | tail -n 1)"
    [[ "$redis_capacity" =~ ^[0-9]+$ ]] || {
        printf 'Refusing: Redis capacity could not be determined.\n' >&2
        return 1
    }
    export BYTEDEPTH_TEST_REDIS_CAPACITY="$redis_capacity"
}

load_manifest_env() {
    local env_file="$1" key value

    [[ -f "$env_file" && ! -L "$env_file" ]] || {
        printf 'Refusing: generated staging profile environment file is missing.\n' >&2
        return 1
    }
    while IFS='=' read -r key value || [[ -n "$key" ]]; do
        [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ && -n "$value" ]] || {
            printf 'Refusing: malformed staging profile environment file.\n' >&2
            return 1
        }
        export "$key=$value"
    done < "$env_file"
}

redact_secrets() {
    local line secret
    local -a secrets=(
        "${REDISCLI_AUTH:-}"
        "${BYTEDEPTH_TEST_MEILI_API_KEY:-}"
        "${BYTEDEPTH_STAGING_IT_DATASOURCE_PASSWORD:-}"
        "${BYTEDEPTH_STAGING_IT_REDIS_PASSWORD:-}"
    )

    while IFS= read -r line || [[ -n "$line" ]]; do
        for secret in "${secrets[@]}"; do
            [[ -n "$secret" ]] && line="${line//"$secret"/[REDACTED]}"
        done
        printf '%s\n' "$line"
    done
}

cleanup_slot() {
    local cleanup_status=0

    if [[ -n "$manifest" && -f "$manifest" ]]; then
        if ! BYTEDEPTH_TEST_SLOT_LOCK_HELD=1 BYTEDEPTH_DEPLOY_MODE=staging \
            "$SLOT_TEARDOWN" --manifest "$manifest"; then
            cleanup_status=1
        fi
    elif (( app_stopped != 0 )); then
        systemctl start bytedepth-app.service || cleanup_status=1
        systemctl is-active --quiet bytedepth-app.service || cleanup_status=1
    fi
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

    if (( cleanup_done == 0 )); then
        cleanup_slot || status=1
    fi
    if [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]]; then
        rm -rf -- "$WORK_DIR"
    fi
    exit "$status"
}
trap on_exit EXIT

if [[ "${1:-}" != '--lock-held' ]]; then
    install -d -o root -g root -m 0700 "$STATE_DIR"
    exec env BYTEDEPTH_TEST_SLOT_LOCK_HELD=1 flock -x "$LOCK_FILE" "$0" --lock-held "$@"
fi
shift

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = substr($0, index($0, "=") + 1)} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
[[ "$deploy_mode" == staging ]] || {
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging, got %s\n' "${deploy_mode:-unset}" >&2
    exit 1
}

invalidate_evidence
tested_commit="$(read_checked_out_commit)"
require_deployed_commit "$tested_commit"
require_staging_runtime "$RUNTIME_MANIFEST" "$SOURCE_ROOT"
require_workspace_headroom
require_test_slot_inputs
load_resource_credentials

run_id="$(date -u +%Y%m%d_%H%M%S)_$(openssl rand -hex 4)"
manifest="$TEST_STATE_DIR/$run_id/manifest"
export BYTEDEPTH_TEST_STATE_DIR="$TEST_STATE_DIR"
export BYTEDEPTH_TEST_CANDIDATE_SHA="$tested_commit"
export BYTEDEPTH_TEST_SLOT_LOCK_HELD=1
export BYTEDEPTH_DEPLOY_MODE=staging
install -d -o root -g root -m 0700 "$TEST_STATE_DIR"

systemctl stop bytedepth-app.service
app_stopped=1
if systemctl is-active --quiet bytedepth-app.service; then
    printf 'Refusing: staging app remained active; test resources were not provisioned.\n' >&2
    exit 1
fi

"$SLOT_PROVISION" --run-id "$run_id" --manifest "$manifest"
require_manifest "$manifest"
it_env="$(slot_manifest_value "$manifest" it_env)"
load_manifest_env "$it_env"
[[ "${SPRING_PROFILES_ACTIVE:-}" == staging-it ]] || {
    printf 'Refusing: provisioned IT environment did not select staging-it.\n' >&2
    exit 1
}
export BYTEDEPTH_TEST_MANIFEST="$manifest"
manifest_sha="$(shasum -a 256 "$manifest" | awk '{print $1}')"
[[ "$manifest_sha" =~ ^[0-9a-f]{64}$ ]] || {
    printf 'Refusing: unable to digest the test resource manifest.\n' >&2
    exit 1
}

WORK_DIR="$(mktemp -d "$STATE_DIR/.staging-integration.XXXXXX")"
chmod 0700 "$WORK_DIR"
MAVEN_LOG="$WORK_DIR/maven.log"
[[ -d "$SHARED_MAVEN_REPOSITORY" ]] || {
    printf 'Refusing: shared staging Maven repository is unavailable. Run bootstrap-staging-runtime.sh.\n' >&2
    exit 1
}
cd "$SOURCE_ROOT"
if ! ./mvnw -o -Pstaging-integration verify -Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY" 2>&1 \
    | redact_secrets | tee "$MAVEN_LOG"; then
    printf 'Staging integration tests failed.\n' >&2
    exit 1
fi
warning_policy_check_file "$MAVEN_LOG" || {
    printf 'Refusing: Maven output contains an unallowlisted WARN or WARNING.\n' >&2
    exit 1
}

cleanup_slot
[[ "$cleanup_result" == passed ]] || {
    printf 'Refusing: test resource cleanup or staging restoration failed.\n' >&2
    exit 1
}
if [[ "$(read_checked_out_commit)" != "$tested_commit" ]]; then
    printf 'Refusing: checked-out commit changed during staging integration tests.\n' >&2
    exit 1
fi
require_deployed_commit "$tested_commit"

install -d -o root -g root -m 0700 "$EVIDENCE_DIR"
evidence_tmp="$(mktemp "$EVIDENCE_DIR/.staging-integration.XXXXXX")"
printf 'runtime_mode=host-native\nrun_id=%s\ntest_resource_manifest_sha=%s\ncleanup=result=passed\ncommit=%s\ncommand=run-staging-integration-tests\ntimestamp=%s\nresult=passed\n' \
    "$run_id" "$manifest_sha" "$tested_commit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence_tmp"
install -o root -g root -m 0600 "$evidence_tmp" "$EVIDENCE_DIR/staging-integration"
rm -f -- "$evidence_tmp"
printf 'Staging integration tests passed for %s.\n' "$tested_commit"
