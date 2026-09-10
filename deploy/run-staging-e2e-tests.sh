#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-e2e-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history
readonly CHROMIUM_EXECUTABLE=/usr/bin/chromium
readonly WORK_DIR="$(mktemp -d)"
readonly E2E_LOG="$WORK_DIR/playwright.log"
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
    evidence_tmp="$(mktemp "$EVIDENCE_DIR/.staging-e2e.XXXXXX")"
    printf 'commit=%s\ncommand=run-staging-e2e-tests\ntimestamp=%s\nresult=passed\n' \
        "$commit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence_tmp"
    install -o root -g root -m 0600 "$evidence_tmp" "$EVIDENCE_DIR/staging-e2e"
    rm -f "$evidence_tmp"
}

deploy_mode="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value = substr($0, index($0, "=") + 1)} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != 'staging' ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging, got %s\n' "${deploy_mode:-unset}" >&2
    exit 1
fi

if [[ ! -x "$CHROMIUM_EXECUTABLE" ]]; then
    printf 'Refusing: staging Chromium executable is unavailable at %s\n' "$CHROMIUM_EXECUTABLE" >&2
    exit 1
fi

cd "$SOURCE_ROOT"
if ! E2E_BASE_URL='https://staging.bytedepth.cn' \
    PLAYWRIGHT_CHROMIUM_EXECUTABLE="$CHROMIUM_EXECUTABLE" \
    npm run test:e2e 2>&1 | tee "$E2E_LOG"; then
    printf 'Staging E2E tests failed.\n' >&2
    exit 1
fi

if grep -qi 'warning' "$E2E_LOG"; then
    printf 'Refusing: Playwright output contains WARNING.\n' >&2
    exit 1
fi

write_evidence
printf 'Staging E2E tests passed.\n'
