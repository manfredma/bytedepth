#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly RUNNER="$SOURCE_ROOT/deploy/run-staging-e2e-tests.sh"
readonly TEST_SLOT_SERVICE="$SOURCE_ROOT/deploy/systemd/bytedepth-test-slot.service"
TEMP_ROOT="$(mktemp -d)"
readonly TEMP_ROOT
readonly FIXTURE_ROOT="$TEMP_ROOT/fixture"
readonly FIXTURE_SOURCE="$FIXTURE_ROOT/source"
readonly FIXTURE_CONFIG="$FIXTURE_ROOT/bytedepth-deploy.conf"
readonly EVIDENCE_DIR="$FIXTURE_ROOT/test-history"
readonly RUNTIME_MANIFEST="$FIXTURE_ROOT/runtime/manifest"
readonly DEPLOY_HISTORY="$FIXTURE_ROOT/deploy-history"
readonly LOCK_FILE="$FIXTURE_ROOT/deployment-test.lock"
readonly FIXTURE_CHROMIUM="$FIXTURE_ROOT/chromium"
readonly FIXTURE_JAR="$FIXTURE_ROOT/app.jar"
readonly FIXTURE_MYSQL_DEFAULTS="$FIXTURE_ROOT/mysql.defaults"
readonly FIXTURE_REDIS_SECRET="$FIXTURE_ROOT/redis.secret"
readonly FIXTURE_MEILI_SECRET="$FIXTURE_ROOT/meili.secret"
readonly FIXTURE_SQL="$FIXTURE_ROOT/fixture.sql"
readonly FIXTURE_SQL_SHA256="$FIXTURE_ROOT/fixture.sql.sha256"
readonly SYSTEMCTL_STATE="$FIXTURE_ROOT/systemctl.state"
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
[[ -f "$TEST_SLOT_SERVICE" ]] || {
    printf 'Expected native E2E test-slot systemd service.\n' >&2
    exit 1
}
rg -q 'Conflicts=bytedepth-app\.service' "$TEST_SLOT_SERVICE"
rg -q 'EnvironmentFile=.*staging-e2e' "$TEST_SLOT_SERVICE"
rg -q 'BYTEDEPTH_TEST_SLOT_JAR|current/app\.jar' "$TEST_SLOT_SERVICE"
rg -q 'ExecStartPre=.*bytedepth-app\.service|is-active.*bytedepth-app\.service' "$TEST_SLOT_SERVICE"
rg -q 'RequiresMountsFor=/data/images' "$TEST_SLOT_SERVICE"
if [[ ! -x "$RUNNER" ]] || [[ "$(git ls-files -s "$RUNNER" | awk '{print $1}')" != '100755' ]]; then
    printf 'Expected staging E2E runner to be tracked as executable.\n' >&2
    exit 1
fi
grep -Fqx 'readonly CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome' "$RUNNER"
if rg -q '\.e2e/chrome-linux64|playwright install' "$RUNNER"; then
    printf 'Staging E2E runner must not retain a project-local Chromium contract.\n' >&2
    exit 1
fi
ANNOTATION_E2E="$SOURCE_ROOT/tests/e2e/annotation.spec.js"
if rg -q 'window\.scrollTo\(0, 500\)' "$ANNOTATION_E2E"; then
    printf 'Annotation viewport E2E must not use a layout-dependent fixed scroll distance.\n' >&2
    exit 1
fi
rg -q 'window\.scrollBy' "$ANNOTATION_E2E"
rg -q 'expect\.poll' "$ANNOTATION_E2E"
rg -q 'provision-staging-test-slot\.sh' "$RUNNER"
rg -q 'teardown-staging-test-slot\.sh' "$RUNNER"
rg -q 'BYTEDEPTH_STAGING_TEST_SLOT_SERVICE|SLOT_SERVICE' "$RUNNER"
rg -q 'systemctl stop "\$BYTEDEPTH_STAGING_APP_SERVICE"' "$RUNNER"
rg -q 'systemctl start "\$SLOT_SERVICE"' "$RUNNER"
rg -q 'systemctl stop "\$SLOT_SERVICE"' "$RUNNER"
rg -q 'SPRING_PROFILES_ACTIVE.*staging-e2e|== staging-e2e' "$RUNNER"
rg -q 'BYTEDEPTH_ENVIRONMENT=staging' "$SOURCE_ROOT/deploy/provision-staging-test-slot.sh"
rg -q 'BYTEDEPTH_STAGING_APP_PORT' "$SOURCE_ROOT/deploy/provision-staging-test-slot.sh"
rg -q 'BYTEDEPTH_TEST_MANIFEST|run_id=' "$RUNNER"
rg -q 'cleanup|restore' "$RUNNER"
rg -q 'state-uncertain' "$RUNNER"
rg -q 'runtime_mode=host-native|test_resource_manifest_sha=|cleanup=result=passed' "$RUNNER"

mkdir -p "$FIXTURE_SOURCE/deploy/lib" "$FAKE_BIN" "$(dirname "$RUNTIME_MANIFEST")"
printf 'lockfile\n' > "$FIXTURE_SOURCE/package-lock.json"
printf '<project/>\n' > "$FIXTURE_SOURCE/pom.xml"
printf 'jar fixture\n' > "$FIXTURE_JAR"
printf '[client]\nuser=root\n' > "$FIXTURE_MYSQL_DEFAULTS"
printf 'redis-test-secret\n' > "$FIXTURE_REDIS_SECRET"
printf 'meili-test-secret\n' > "$FIXTURE_MEILI_SECRET"
printf 'fixture sql\n' > "$FIXTURE_SQL"
shasum -a 256 "$FIXTURE_SQL" | awk '{print $1}' > "$FIXTURE_SQL_SHA256"
chmod 600 "$FIXTURE_MYSQL_DEFAULTS" "$FIXTURE_REDIS_SECRET" "$FIXTURE_MEILI_SECRET" "$FIXTURE_SQL" "$FIXTURE_SQL_SHA256"
cat > "$FIXTURE_CHROMIUM" <<'SCRIPT'
#!/usr/bin/env bash
printf 'Google Chrome for Testing 151.0.7922.34\n'
SCRIPT
chmod +x "$FIXTURE_CHROMIUM"
sed 's@^readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome$@readonly SHARED_CHROMIUM_EXECUTABLE='"$FIXTURE_CHROMIUM"'@' \
    "$SOURCE_ROOT/deploy/lib/staging-runtime.sh" > "$FIXTURE_SOURCE/deploy/lib/staging-runtime.sh"
cp "$SOURCE_ROOT/deploy/lib/warning-policy.sh" "$FIXTURE_SOURCE/deploy/lib/warning-policy.sh"
cp "$SOURCE_ROOT/deploy/lib/staging-test-slot.sh" "$FIXTURE_SOURCE/deploy/lib/staging-test-slot.sh"
cp "$SOURCE_ROOT/deploy/lib/staging-native-target.sh" "$FIXTURE_SOURCE/deploy/lib/staging-native-target.sh"
cat >> "$FIXTURE_SOURCE/deploy/lib/staging-test-slot.sh" <<'SCRIPT'
slot_root_private() { return 0; }
slot_root_directory() { [[ -d "$1" && ! -L "$1" ]]; }
SCRIPT
cat > "$FIXTURE_SOURCE/deploy/provision-staging-test-slot.sh" <<'SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail
while [[ $# -gt 0 ]]; do
    case "$1" in
        --run-id) run_id="$2"; shift 2 ;;
        --manifest) manifest="$2"; shift 2 ;;
        *) exit 2 ;;
    esac
done
run_dir="$(dirname "$manifest")"
mkdir -p "$run_dir"
cat > "$run_dir/staging-e2e.env" <<EOF
SPRING_PROFILES_ACTIVE=staging-e2e
BYTEDEPTH_STAGING_E2E_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/bytedepth_e2e_$run_id
BYTEDEPTH_STAGING_E2E_DATASOURCE_USERNAME=fixture-e2e
BYTEDEPTH_STAGING_E2E_DATASOURCE_PASSWORD=fixture-db-password
BYTEDEPTH_STAGING_E2E_REDIS_HOST=127.0.0.1
BYTEDEPTH_STAGING_E2E_REDIS_PORT=6379
BYTEDEPTH_STAGING_E2E_REDIS_DATABASE=15
BYTEDEPTH_STAGING_E2E_REDIS_PASSWORD=fixture-redis-password
BYTEDEPTH_STAGING_E2E_REDIS_SESSION_NAMESPACE=bytedepth:e2e:$run_id:
BYTEDEPTH_STAGING_E2E_REDIS_KEY_NAMESPACE=bytedepth:e2e:$run_id:
BYTEDEPTH_STAGING_E2E_SEARCH_INDEX=posts_e2e_$run_id
BYTEDEPTH_STAGING_E2E_SEARCH_API_KEY=fixture-search-key
BYTEDEPTH_STAGING_E2E_UPLOAD_IMAGE_DIR=$run_dir/images
EOF
chmod 600 "$run_dir/staging-e2e.env"
cat > "$manifest" <<EOF
run_id=$run_id
candidate_sha=$STAGING_E2E_SHA
mode=staging
it_db=bytedepth_it_$run_id
it_user=bd_it_$run_id
it_index=posts_it_$run_id
it_namespace=bytedepth:it:$run_id:
it_redis_db=14
it_key_uid=11111111-1111-1111-1111-111111111111
e2e_db=bytedepth_e2e_$run_id
e2e_user=bd_e2e_$run_id
e2e_index=posts_e2e_$run_id
e2e_namespace=bytedepth:e2e:$run_id:
e2e_redis_db=15
e2e_key_uid=22222222-2222-2222-2222-222222222222
app_port=8080
it_env=$run_dir/staging-it.env
e2e_env=$run_dir/staging-e2e.env
EOF
chmod 600 "$manifest"
mkdir -p "$run_dir/images"
SCRIPT
chmod +x "$FIXTURE_SOURCE/deploy/provision-staging-test-slot.sh"
cat > "$FIXTURE_SOURCE/deploy/teardown-staging-test-slot.sh" <<'SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'app-active\n' > "$STAGING_E2E_SYSTEMCTL_STATE"
SCRIPT
chmod +x "$FIXTURE_SOURCE/deploy/teardown-staging-test-slot.sh"
printf 'ref=main\ncommit=%s\ndeployed_at=2026-09-10T10:11:12Z\n---\n' "$CURRENT_SHA" > "$DEPLOY_HISTORY"

sed \
    -e "s@^readonly SOURCE_ROOT=/opt/bytedepth\$@readonly SOURCE_ROOT=$FIXTURE_SOURCE@" \
    -e "s@^readonly CONFIG_FILE=/etc/bytedepth-deploy.conf\$@readonly CONFIG_FILE=$FIXTURE_CONFIG@" \
    -e "s@^readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history\$@readonly EVIDENCE_DIR=$EVIDENCE_DIR@" \
    -e "s@^readonly RUNTIME_MANIFEST=/var/lib/bytedepth-staging/runtime/manifest\$@readonly RUNTIME_MANIFEST=$RUNTIME_MANIFEST@" \
    -e "s@^readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history\$@readonly DEPLOY_HISTORY=$DEPLOY_HISTORY@" \
    -e "s@^readonly LOCK_FILE=/var/lib/bytedepth-staging/deployment-test.lock\$@readonly LOCK_FILE=$LOCK_FILE@" \
    -e "s@^readonly TEST_STATE_DIR=/var/lib/bytedepth-staging/test-slots\$@readonly TEST_STATE_DIR=$FIXTURE_ROOT/test-slots@" \
    -e "s@^readonly SLOT_PROVISION=/opt/bytedepth/deploy/provision-staging-test-slot.sh\$@readonly SLOT_PROVISION=$FIXTURE_SOURCE/deploy/provision-staging-test-slot.sh@" \
    -e "s@^readonly SLOT_TEARDOWN=/opt/bytedepth/deploy/teardown-staging-test-slot.sh\$@readonly SLOT_TEARDOWN=$FIXTURE_SOURCE/deploy/teardown-staging-test-slot.sh@" \
    -e "s@^[[:space:]]*readonly SLOT_ENV=/run/bytedepth/staging-e2e.env\$@    readonly SLOT_ENV=$FIXTURE_ROOT/staging-e2e.env@" \
    -e "s@^readonly SLOT_RUNTIME_DIR=/run/bytedepth\$@readonly SLOT_RUNTIME_DIR=$FIXTURE_ROOT/runtime@" \
    -e "s@^readonly SLOT_JAR=/opt/bytedepth/current/app.jar\$@readonly SLOT_JAR=$FIXTURE_JAR@" \
    -e "s@^readonly CHROMIUM_EXECUTABLE=.*\$@readonly CHROMIUM_EXECUTABLE=$FIXTURE_CHROMIUM@" \
    -e '/^if \[\[ "${EUID}" -ne 0 \]\]; then$/,/^fi$/d' \
    "$RUNNER" > "$TEMP_ROOT/runner"
chmod +x "$TEMP_ROOT/runner"
bash -c 'source "$1"; write_runtime_manifest "$2" "$3"' -- \
    "$FIXTURE_SOURCE/deploy/lib/staging-runtime.sh" "$RUNTIME_MANIFEST" "$FIXTURE_SOURCE"

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
printf 'E2E_BASE_URL=%s\nE2E_POST_SLUG=%s\nE2E_ADMIN_USERNAME=%s\nPLAYWRIGHT_CHROMIUM_EXECUTABLE=%s\n' "$E2E_BASE_URL" "$E2E_POST_SLUG" "$E2E_ADMIN_USERNAME" "$PLAYWRIGHT_CHROMIUM_EXECUTABLE" > "$STAGING_E2E_NPM_ENV"
[[ "$E2E_BASE_URL" == 'https://staging-bytedepth.bytedepth.cn' ]]
[[ "$E2E_POST_SLUG" == 'staging-e2e-fixture' ]]
[[ "$E2E_ADMIN_USERNAME" == 'fixture-e2e-admin' ]]
[[ "$E2E_ADMIN_PASSWORD" == 'fixture-e2e-password' ]]
[[ "$PLAYWRIGHT_CHROMIUM_EXECUTABLE" == "$STAGING_E2E_CHROMIUM" ]]
[[ -s "$STAGING_E2E_GIT_LOG" ]]
printf '%s\n' "${STAGING_E2E_NPM_OUTPUT:-Playwright passed}"
exit "${STAGING_E2E_NPM_EXIT:-0}"
SCRIPT
chmod +x "$FAKE_BIN/npm"

cat > "$FAKE_BIN/curl" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STAGING_E2E_CURL_ARGS"
if [[ "${*: -1}" == */version ]]; then
    printf '{"commit":"%s"}\n' "$STAGING_E2E_SHA"
else
    printf '<a href="/posts/staging-e2e-fixture">fixture</a>\n'
fi
SCRIPT
chmod +x "$FAKE_BIN/curl"

cat > "$FAKE_BIN/redis-cli" <<'SCRIPT'
#!/usr/bin/env bash
printf 'databases\n16\n'
SCRIPT
chmod +x "$FAKE_BIN/redis-cli"

cat > "$FAKE_BIN/systemctl" <<'SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail
action="$1"
shift
[[ "${1:-}" == --quiet ]] && shift
service="${1:-}"
state='app-active'
[[ -f "$STAGING_E2E_SYSTEMCTL_STATE" ]] && state="$(< "$STAGING_E2E_SYSTEMCTL_STATE")"
case "$action:$service" in
    is-active:bytedepth-app.service) [[ "$state" == app-active ]] ;;
    stop:bytedepth-app.service) printf 'app-stopped\n' > "$STAGING_E2E_SYSTEMCTL_STATE" ;;
    start:bytedepth-app.service) printf 'app-active\n' > "$STAGING_E2E_SYSTEMCTL_STATE" ;;
    is-active:bytedepth-test-slot.service) [[ "$state" == slot-active ]] ;;
    start:bytedepth-test-slot.service) printf 'slot-active\n' > "$STAGING_E2E_SYSTEMCTL_STATE" ;;
    stop:bytedepth-test-slot.service) printf 'slot-stopped\n' > "$STAGING_E2E_SYSTEMCTL_STATE" ;;
    *) exit 2 ;;
esac
SCRIPT
chmod +x "$FAKE_BIN/systemctl"

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
        STAGING_E2E_SYSTEMCTL_STATE="$SYSTEMCTL_STATE" \
        STAGING_E2E_SHA="$CURRENT_SHA" \
        BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE="$FIXTURE_MYSQL_DEFAULTS" \
        BYTEDEPTH_TEST_REDIS_SECRET_FILE="$FIXTURE_REDIS_SECRET" \
        BYTEDEPTH_TEST_MEILI_SECRET_FILE="$FIXTURE_MEILI_SECRET" \
        BYTEDEPTH_TEST_FIXTURE="$FIXTURE_SQL" \
        BYTEDEPTH_TEST_FIXTURE_SHA256_FILE="$FIXTURE_SQL_SHA256" \
        BYTEDEPTH_TEST_STAGING_REDIS_DB=1 \
        BYTEDEPTH_TEST_IT_REDIS_DB=14 \
        BYTEDEPTH_TEST_E2E_REDIS_DB=15 \
        STAGING_E2E_CHROMIUM="$FIXTURE_CHROMIUM" \
        BYTEDEPTH_STAGING_E2E_USERNAME='fixture-e2e-admin' \
        BYTEDEPTH_STAGING_E2E_PASSWORD='fixture-e2e-password' \
        STAGING_E2E_NPM_OUTPUT="${STAGING_E2E_NPM_OUTPUT:-}" \
        STAGING_E2E_NPM_EXIT="${STAGING_E2E_NPM_EXIT:-0}" \
        "$TEMP_ROOT/runner" > "$RUNNER_OUTPUT" 2>&1 || {
            cat "$RUNNER_OUTPUT" >&2
            return 1
        }
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
grep -Fqx 'E2E_BASE_URL=https://staging-bytedepth.bytedepth.cn' "$NPM_ENV"
grep -Fqx 'E2E_POST_SLUG=staging-e2e-fixture' "$NPM_ENV"
grep -Fqx 'E2E_ADMIN_USERNAME=fixture-e2e-admin' "$NPM_ENV"
grep -Fqx "PLAYWRIGHT_CHROMIUM_EXECUTABLE=$FIXTURE_CHROMIUM" "$NPM_ENV"
grep -Fqx "commit=$CURRENT_SHA" "$EVIDENCE_DIR/staging-e2e"
grep -Fqx 'command=run-staging-e2e-tests' "$EVIDENCE_DIR/staging-e2e"
grep -Eq '^timestamp=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' "$EVIDENCE_DIR/staging-e2e"
grep -Fqx 'result=passed' "$EVIDENCE_DIR/staging-e2e"
grep -Fqx 'runtime_mode=host-native' "$EVIDENCE_DIR/staging-e2e"
grep -Fqx 'cleanup=result=passed' "$EVIDENCE_DIR/staging-e2e"
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
