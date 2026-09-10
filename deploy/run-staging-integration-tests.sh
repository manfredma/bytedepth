#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-integration-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly WORK_DIR="$(mktemp -d)"
readonly MAVEN_LOG="$WORK_DIR/maven.log"
trap 'rm -rf "$WORK_DIR"' EXIT

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

printf 'Staging integration tests passed.\n'
