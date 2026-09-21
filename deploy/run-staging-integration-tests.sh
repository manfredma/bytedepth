#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-integration-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history
readonly RUNTIME_MANIFEST=/var/lib/bytedepth-staging/runtime/manifest
readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history
readonly LOCK_FILE=/var/lib/bytedepth-staging/deployment-test.lock
readonly DOCKER_SOCKET=/var/run/docker.sock
readonly MINIMUM_WORKSPACE_FREE_KIB=2097152
source "$SOURCE_ROOT/deploy/lib/staging-runtime.sh"
source "$SOURCE_ROOT/deploy/lib/warning-policy.sh"
WORK_DIR="$(mktemp -d)"
readonly WORK_DIR
readonly MAVEN_LOG="$WORK_DIR/maven.log"
readonly MAVEN_ENV_FILE="$WORK_DIR/maven.env"
readonly MAVEN_SETTINGS_FILE="$WORK_DIR/settings.xml"
trap 'rm -rf "$WORK_DIR"' EXIT

# Run the complete test/evidence transaction under the same lock as staging
# deployment.  The re-exec keeps flock alive for this process and every child.
if [[ "${1:-}" != '--lock-held' ]]; then
    install -d -o root -g root -m 0700 "$(dirname "$LOCK_FILE")"
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
    rm -f "$EVIDENCE_DIR/staging-integration"
}

require_docker_socket() {
    if [[ ! -e "$DOCKER_SOCKET" ]]; then
        printf 'Refusing: Docker daemon socket is unavailable at %s; Testcontainers requires it.\n' "$DOCKER_SOCKET" >&2
        exit 1
    fi

    if ! sudo docker -H "unix://$DOCKER_SOCKET" info >/dev/null; then
        printf 'Refusing: Docker daemon socket is not usable at %s; Testcontainers requires it.\n' "$DOCKER_SOCKET" >&2
        exit 1
    fi
}

require_workspace_headroom() {
    local available_kib

    available_kib="$(df -Pk "$WORK_DIR" | awk 'NR == 2 {print $4}')"
    if [[ ! "$available_kib" =~ ^[0-9]+$ || "$available_kib" -lt "$MINIMUM_WORKSPACE_FREE_KIB" ]]; then
        printf 'Refusing: staging workspace needs at least %s KiB free, found %s KiB. Prune unused Docker cache before retrying.\n' \
            "$MINIMUM_WORKSPACE_FREE_KIB" "${available_kib:-unknown}" >&2
        exit 1
    fi
}

redact_redis_password() {
    local line

    while IFS= read -r line || [[ -n "$line" ]]; do
        printf '%s\n' "${line//"$redis_password"/[REDACTED]}"
    done
}

write_evidence() {
    local tested_commit="$1"
    local evidence_tmp

    if [[ "$(read_checked_out_commit)" != "$tested_commit" ]]; then
        printf 'Refusing: checked-out commit changed during staging integration tests.\n' >&2
        exit 1
    fi
    require_deployed_commit "$tested_commit"

    install -d -o root -g root -m 0700 "$EVIDENCE_DIR"
    evidence_tmp="$(mktemp "$EVIDENCE_DIR/.staging-integration.XXXXXX")"
    printf 'commit=%s\ncommand=run-staging-integration-tests\ntimestamp=%s\nresult=passed\n' \
        "$tested_commit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence_tmp"
    install -o root -g root -m 0600 "$evidence_tmp" "$EVIDENCE_DIR/staging-integration"
    rm -f "$evidence_tmp"
}

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = substr($0, index($0, "=") + 1)} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != 'staging' ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging, got %s\n' "${deploy_mode:-unset}" >&2
    exit 1
fi

invalidate_evidence
tested_commit="$(read_checked_out_commit)"
require_deployed_commit "$tested_commit"
require_staging_runtime "$RUNTIME_MANIFEST" "$SOURCE_ROOT"
require_docker_socket

redis_password="$(awk '$0 ~ /^REDIS_PASSWORD=/ {value = substr($0, index($0, "=") + 1)} END {print value}' "$SOURCE_ROOT/.env" 2>/dev/null || true)"
if [[ -z "${redis_password//[[:space:]]/}" ]]; then
    printf 'Refusing: REDIS_PASSWORD is missing or blank in .env\n' >&2
    exit 1
fi

[[ -d "$SHARED_MAVEN_REPOSITORY" ]] || { printf 'Refusing: shared staging Maven repository is unavailable. Run bootstrap-staging-runtime.sh.\n' >&2; exit 1; }
require_workspace_headroom
mkdir -p "$WORK_DIR/source"
umask 077
printf 'BYTEDEPTH_IT_REDIS_PASSWORD=%s\n' "$redis_password" > "$MAVEN_ENV_FILE"
# Archive the exact commit rather than copying the checkout.  A checkout copy
# brings .git, node_modules and target output into /tmp, which is neither part
# of the tested source nor acceptable on a finite staging disk.
git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" archive --format=tar "$tested_commit" \
    | tar -x -C "$WORK_DIR/source"
# git archive excludes the untracked staging .env.  MAVEN_ENV_FILE remains the
# only credential channel mounted in the disposable test container.
# The repository's .mvn/maven.config explicitly selects .mvn/settings.xml.
# Preserve the checked-out repository's own Maven mirror policy in this disposable
# workspace while avoiding any host-local Maven configuration leakage.
install -d -m 0700 "$WORK_DIR/source/.mvn"
install -m 0600 "$SOURCE_ROOT/.mvn/settings.xml" "$MAVEN_SETTINGS_FILE"
install -m 0600 "$MAVEN_SETTINGS_FILE" "$WORK_DIR/source/.mvn/settings.xml"

if ! sudo docker run --rm --network bytedepth_default \
    --add-host host.docker.internal:host-gateway \
    --env-file "$MAVEN_ENV_FILE" \
    --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    -v "$WORK_DIR/source":/workspace \
    -v "$SHARED_MAVEN_REPOSITORY":/root/.m2/repository:ro \
    -v "$MAVEN_SETTINGS_FILE:/root/.m2/settings.xml:ro" \
    -v "$DOCKER_SOCKET:$DOCKER_SOCKET" \
    -w /workspace \
    "$STAGING_MAVEN_IMAGE" \
    mvn -o -Pstaging-integration test-compile failsafe:integration-test failsafe:verify \
    -Dbytedepth.it.redis.host=redis \
    -Dbytedepth.it.redis.port=6379 2>&1 | redact_redis_password | tee "$MAVEN_LOG"; then
    printf 'Staging integration tests failed.\n' >&2
    exit 1
fi

if ! warning_policy_check_file "$MAVEN_LOG"; then
    printf 'Refusing: Maven output contains an unallowlisted WARN or WARNING.\n' >&2
    exit 1
fi

write_evidence "$tested_commit"
printf 'Staging integration tests passed.\n'
