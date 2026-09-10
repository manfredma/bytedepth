#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/run-staging-e2e-tests.sh\n' >&2
    exit 1
fi

readonly SOURCE_ROOT=/opt/bytedepth
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history
readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history
readonly LOCK_FILE=/var/lib/bytedepth-staging/deployment-test.lock
# Chromium is provisioned by Playwright in the staging checkout.  Do not use
# Ubuntu's chromium-browser package: on 22.04 it is a slow Snap transition
# package and is not the browser version pinned by this project's E2E suite.
readonly CHROMIUM_EXECUTABLE="$SOURCE_ROOT/.e2e/chrome-linux64/chrome"
readonly WORK_DIR="$(mktemp -d)"
readonly E2E_LOG="$WORK_DIR/playwright.log"
trap 'rm -rf "$WORK_DIR"' EXIT

# Deployment, integration tests and E2E share this lock so an evidence record
# can only be written for a stable deployed checkout.
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
    rm -f "$EVIDENCE_DIR/staging-e2e"
}

write_evidence() {
    local tested_commit="$1"
    local evidence_tmp

    if [[ "$(read_checked_out_commit)" != "$tested_commit" ]]; then
        printf 'Refusing: checked-out commit changed during staging E2E tests.\n' >&2
        exit 1
    fi
    require_deployed_commit "$tested_commit"

    install -d -o root -g root -m 0700 "$EVIDENCE_DIR"
    evidence_tmp="$(mktemp "$EVIDENCE_DIR/.staging-e2e.XXXXXX")"
    printf 'commit=%s\ncommand=run-staging-e2e-tests\ntimestamp=%s\nresult=passed\n' \
        "$tested_commit" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$evidence_tmp"
    install -o root -g root -m 0600 "$evidence_tmp" "$EVIDENCE_DIR/staging-e2e"
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

write_evidence "$tested_commit"
printf 'Staging E2E tests passed.\n'
