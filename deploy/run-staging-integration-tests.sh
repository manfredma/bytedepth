#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-integration-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history
readonly WORK_DIR="$(mktemp -d)"
readonly MAVEN_LOG="$WORK_DIR/maven.log"
trap 'rm -rf "$WORK_DIR"' EXIT

write_evidence() {
    local commit
    local evidence_tmp

    commit="$(git -C "$SOURCE_ROOT" rev-parse HEAD)"
    if [[ ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
        printf 'Refusing: unable to determine the full checked-out commit SHA.\n' >&2
        exit 1
    fi

    install -d -o root -g root -m 0700 "$EVIDENCE_DIR"
    evidence_tmp="$(mktemp "$EVIDENCE_DIR/.staging-integration.XXXXXX")"
    printf 'commit=%s\ncommand=run-staging-integration-tests\ntimestamp=%s\nresult=passed\n' \
        "$commit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence_tmp"
    install -o root -g root -m 0600 "$evidence_tmp" "$EVIDENCE_DIR/staging-integration"
    rm -f "$evidence_tmp"
}

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = substr($0, index($0, "=") + 1)} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != 'staging' ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging, got %s\n' "${deploy_mode:-unset}" >&2
    exit 1
fi

redis_password="$(awk '$0 ~ /^REDIS_PASSWORD=/ {value = substr($0, index($0, "=") + 1)} END {print value}' "$SOURCE_ROOT/.env" 2>/dev/null || true)"
if [[ -z "${redis_password//[[:space:]]/}" ]]; then
    printf 'Refusing: REDIS_PASSWORD is missing or blank in .env\n' >&2
    exit 1
fi

mkdir -p "$WORK_DIR/source" "$WORK_DIR/m2"
cp -a "$SOURCE_ROOT/." "$WORK_DIR/source/"

if ! sudo docker run --rm --network bytedepth_default \
    -v "$WORK_DIR/source":/workspace \
    -v "$WORK_DIR/m2":/root/.m2 \
    -w /workspace \
    maven:3.9-eclipse-temurin-25 \
    mvn -Pstaging-integration verify \
    -Dbytedepth.it.redis.host=redis \
    -Dbytedepth.it.redis.port=6379 \
    -Dbytedepth.it.redis.password="$redis_password" 2>&1 | tee "$MAVEN_LOG"; then
    printf 'Staging integration tests failed.\n' >&2
    exit 1
fi

if grep -qi 'warning' "$MAVEN_LOG"; then
    printf 'Refusing: Maven output contains WARNING.\n' >&2
    exit 1
fi

write_evidence
printf 'Staging integration tests passed.\n'
