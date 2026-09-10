#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RUNNER="$SOURCE_ROOT/deploy/run-staging-e2e-tests.sh"
readonly TEMP_ROOT="$(mktemp -d)"
readonly FIXTURE_ROOT="$TEMP_ROOT/fixture"
readonly FIXTURE_SOURCE="$FIXTURE_ROOT/source"
readonly FIXTURE_CONFIG="$FIXTURE_ROOT/bytedepth-deploy.conf"
readonly EVIDENCE_DIR="$FIXTURE_ROOT/test-history"
readonly DEPLOY_HISTORY="$FIXTURE_ROOT/deploy-history"
readonly LOCK_FILE="$FIXTURE_ROOT/deployment-test.lock"
readonly FIXTURE_CHROMIUM="$FIXTURE_ROOT/chromium"
readonly FAKE_BIN="$TEMP_ROOT/bin"
readonly NPM_ARGS="$TEMP_ROOT/npm.args"
readonly NPM_ENV="$TEMP_ROOT/npm.env"
readonly CURL_ARGS="$TEMP_ROOT/curl.args"
readonly GIT_LOG="$TEMP_ROOT/git.log"
readonly FLOCK_ARGS="$TEMP_ROOT/flock.args"
readonly INSTALL_ARGS="$TEMP_ROOT/install.args"
readonly RUNNER_OUTPUT="$TEMP_ROOT/runner.out"
readonly CURRENT_SHA='0123456789abcdef0123456789abcdef01234567'
trap 'rm -rf "$TEMP_ROOT"' EXIT

if [[ ! -f "$RUNNER" ]]; then
    printf 'Expected staging E2E runner at %s\n' "$RUNNER" >&2
    exit 1
fi
if [[ ! -x "$RUNNER" ]] || [[ "$(git ls-files -s "$RUNNER" | awk '{print $1}')" != '100755' ]]; then
    printf 'Expected staging E2E runner to be tracked as executable.\n' >&2
    exit 1
fi
grep -Fqx 'readonly CHROMIUM_EXECUTABLE="$SOURCE_ROOT/.e2e/chrome-linux64/chrome"' "$RUNNER"

mkdir -p "$FIXTURE_SOURCE" "$FAKE_BIN"
touch "$FIXTURE_CHROMIUM"
chmod +x "$FIXTURE_CHROMIUM"
printf 'ref=main\ncommit=%s\ndeployed_at=2026-09-10T10:11:12Z\n---\n' "$CURRENT_SHA" > "$DEPLOY_HISTORY"

sed \
    -e "s@^readonly SOURCE_ROOT=/opt/bytedepth\$@readonly SOURCE_ROOT=$FIXTURE_SOURCE@" \
    -e "s@^readonly CONFIG_FILE=/etc/bytedepth-deploy.conf\$@readonly CONFIG_FILE=$FIXTURE_CONFIG@" \
    -e "s@^readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history\$@readonly EVIDENCE_DIR=$EVIDENCE_DIR@" \
    -e "s@^readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history\$@readonly DEPLOY_HISTORY=$DEPLOY_HISTORY@" \
    -e "s@^readonly LOCK_FILE=/var/lib/bytedepth-staging/deployment-test.lock\$@readonly LOCK_FILE=$LOCK_FILE@" \
    -e "s@^readonly CHROMIUM_EXECUTABLE=.*\$@readonly CHROMIUM_EXECUTABLE=$FIXTURE_CHROMIUM@" \
    -e '/^if \[\[ "${EUID}" -ne 0 \]\]; then$/,/^fi$/d' \
    "$RUNNER" > "$TEMP_ROOT/runner"
chmod +x "$TEMP_ROOT/runner"

cat > "$FAKE_BIN/flock" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STAGING_E2E_FLOCK_ARGS"
[[ "$1" == '-x' ]]
[[ "$2" == "$STAGING_E2E_LOCK_FILE" ]]
shift 2
[[ "$1" == *runner ]]
exec "$@"
SCRIPT
chmod +x "$FAKE_BIN/flock"

cat > "$FAKE_BIN/npm" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STAGING_E2E_NPM_ARGS"
printf 'E2E_BASE_URL=%s\nE2E_POST_SLUG=%s\nPLAYWRIGHT_CHROMIUM_EXECUTABLE=%s\n' "$E2E_BASE_URL" "$E2E_POST_SLUG" "$PLAYWRIGHT_CHROMIUM_EXECUTABLE" > "$STAGING_E2E_NPM_ENV"
[[ "$E2E_BASE_URL" == 'https://staging.bytedepth.cn' ]]
[[ "$E2E_POST_SLUG" == 'staging-e2e-fixture' ]]
[[ "$PLAYWRIGHT_CHROMIUM_EXECUTABLE" == "$STAGING_E2E_CHROMIUM" ]]
[[ -s "$STAGING_E2E_GIT_LOG" ]]
printf '%s\n' "${STAGING_E2E_NPM_OUTPUT:-Playwright passed}"
exit "${STAGING_E2E_NPM_EXIT:-0}"
SCRIPT
chmod +x "$FAKE_BIN/npm"

cat > "$FAKE_BIN/curl" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STAGING_E2E_CURL_ARGS"
printf '<a href="/posts/staging-e2e-fixture">fixture</a>\n'
SCRIPT
chmod +x "$FAKE_BIN/curl"

cat > "$FAKE_BIN/git" <<'SCRIPT'
#!/usr/bin/env bash
printf 'git %s\n' "$*" >> "$STAGING_E2E_GIT_LOG"
if [[ "$*" == *'rev-parse HEAD'* ]]; then
    count_file="$STAGING_E2E_GIT_COUNT"
    count=0
    [[ -f "$count_file" ]] && count="$(cat "$count_file")"
    count=$((count + 1))
    printf '%s\n' "$count" > "$count_file"
    if [[ "$count" -eq 1 ]]; then
        printf '%s\n' "$STAGING_E2E_SHA"
    else
        printf '%s\n' "${STAGING_E2E_SHA_AFTER:-$STAGING_E2E_SHA}"
    fi
    exit 0
fi
exit 1
SCRIPT
chmod +x "$FAKE_BIN/git"

cat > "$FAKE_BIN/install" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$STAGING_E2E_INSTALL_ARGS"
arguments=()
while [[ "$#" -gt 0 ]]; do
    case "$1" in
        -o|-g)
            shift 2
            ;;
        *)
            arguments+=("$1")
            shift
            ;;
    esac
done
exec /usr/bin/install "${arguments[@]}"
SCRIPT
chmod +x "$FAKE_BIN/install"

write_config() {
    printf 'BYTEDEPTH_DEPLOY_MODE=%s\n' "$1" > "$FIXTURE_CONFIG"
}

run_runner() {
    PATH="$FAKE_BIN:$PATH" \
        STAGING_E2E_NPM_ARGS="$NPM_ARGS" \
        STAGING_E2E_FLOCK_ARGS="$FLOCK_ARGS" \
        STAGING_E2E_CURL_ARGS="$CURL_ARGS" \
        STAGING_E2E_LOCK_FILE="$LOCK_FILE" \
        STAGING_E2E_NPM_ENV="$NPM_ENV" \
        STAGING_E2E_GIT_LOG="$GIT_LOG" \
        STAGING_E2E_GIT_COUNT="$TEMP_ROOT/git.count" \
        STAGING_E2E_INSTALL_ARGS="$INSTALL_ARGS" \
        STAGING_E2E_SHA="$CURRENT_SHA" \
        STAGING_E2E_CHROMIUM="$FIXTURE_CHROMIUM" \
        STAGING_E2E_NPM_OUTPUT="${STAGING_E2E_NPM_OUTPUT:-}" \
        STAGING_E2E_NPM_EXIT="${STAGING_E2E_NPM_EXIT:-0}" \
        "$TEMP_ROOT/runner" > "$RUNNER_OUTPUT" 2>&1
}

# A non-staging host is rejected before Playwright starts.
write_config single-host
if run_runner; then
    printf 'Expected runner to reject a non-staging deployment mode.\n' >&2
    exit 1
fi
[[ ! -e "$NPM_ARGS" ]]

# The wrapper fixes the staging target and installed Chromium, then records the full deployed SHA.
write_config staging
run_runner
grep -Fqx -- '-x' "$FLOCK_ARGS"
grep -Fqx "$LOCK_FILE" "$FLOCK_ARGS"
grep -Fqx 'run' "$NPM_ARGS"
grep -Fqx 'test:e2e' "$NPM_ARGS"
grep -Fqx 'E2E_BASE_URL=https://staging.bytedepth.cn' "$NPM_ENV"
grep -Fqx 'E2E_POST_SLUG=staging-e2e-fixture' "$NPM_ENV"
grep -Fqx "PLAYWRIGHT_CHROMIUM_EXECUTABLE=$FIXTURE_CHROMIUM" "$NPM_ENV"
grep -Fqx "commit=$CURRENT_SHA" "$EVIDENCE_DIR/staging-e2e"
grep -Fqx 'command=run-staging-e2e-tests' "$EVIDENCE_DIR/staging-e2e"
grep -Eq '^timestamp=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' "$EVIDENCE_DIR/staging-e2e"
grep -Fqx 'result=passed' "$EVIDENCE_DIR/staging-e2e"
grep -Fqx -- '-o' "$INSTALL_ARGS"
grep -Fqx 'root' "$INSTALL_ARGS"

# A warning invalidates a previous pass before Playwright starts and cannot mint a replacement.
rm -f "$GIT_LOG" "$TEMP_ROOT/git.count"
if STAGING_E2E_NPM_OUTPUT='WARNING: simulated Playwright warning' run_runner; then
    printf 'Expected runner to reject Playwright warning output.\n' >&2
    exit 1
fi
grep -Fq 'WARNING: simulated Playwright warning' "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-e2e" ]]

# A later failed run likewise invalidates an earlier pass.
rm -f "$GIT_LOG" "$TEMP_ROOT/git.count"
run_runner
[[ -e "$EVIDENCE_DIR/staging-e2e" ]]
rm -f "$GIT_LOG" "$TEMP_ROOT/git.count"
if STAGING_E2E_NPM_EXIT=17 run_runner; then
    printf 'Expected runner to reject failed Playwright.\n' >&2
    exit 1
fi
grep -Fq 'Staging E2E tests failed.' "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-e2e" ]]

# The deployed checkout must not advance while Playwright is running.
rm -f "$GIT_LOG" "$TEMP_ROOT/git.count"
if STAGING_E2E_SHA_AFTER=ffffffffffffffffffffffffffffffffffffffff run_runner; then
    printf 'Expected runner to reject a changed checkout after Playwright.\n' >&2
    exit 1
fi
grep -Fq 'checked-out commit changed during staging E2E tests' "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-e2e" ]]

# A current checkout without a matching deployed-app record is never evidence for the running app.
rm -f "$GIT_LOG" "$TEMP_ROOT/git.count"
run_runner
[[ -e "$EVIDENCE_DIR/staging-e2e" ]]
printf 'ref=main\ncommit=ffffffffffffffffffffffffffffffffffffffff\ndeployed_at=2026-09-10T10:11:12Z\n---\n' > "$DEPLOY_HISTORY"
rm -f "$GIT_LOG" "$TEMP_ROOT/git.count" "$NPM_ARGS"
if run_runner; then
    printf 'Expected runner to reject an app deployment SHA different from the checkout.\n' >&2
    exit 1
fi
grep -Fq 'staging app deployment does not match the tested checkout commit' "$RUNNER_OUTPUT"
[[ ! -e "$NPM_ARGS" ]]
[[ ! -e "$EVIDENCE_DIR/staging-e2e" ]]

printf 'staging E2E runner tests passed\n'
