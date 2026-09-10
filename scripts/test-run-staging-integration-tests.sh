#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RUNNER="$SOURCE_ROOT/deploy/run-staging-integration-tests.sh"
readonly TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

if [[ ! -f "$RUNNER" ]]; then
    printf 'Expected staging integration runner at %s\n' "$RUNNER" >&2
    exit 1
fi

readonly FIXTURE_ROOT="$TEMP_ROOT/fixture"
readonly FIXTURE_SOURCE="$FIXTURE_ROOT/source"
readonly FIXTURE_CONFIG="$FIXTURE_ROOT/bytedepth-deploy.conf"
readonly EVIDENCE_DIR="$FIXTURE_ROOT/test-history"
readonly DEPLOY_HISTORY="$FIXTURE_ROOT/deploy-history"
readonly FAKE_BIN="$TEMP_ROOT/bin"
readonly DOCKER_ARGS="$TEMP_ROOT/docker.args"
readonly GIT_LOG="$TEMP_ROOT/git.log"
readonly INSTALL_ARGS="$TEMP_ROOT/install.args"
readonly RUNNER_OUTPUT="$TEMP_ROOT/runner.out"
readonly REDIS_SECRET='staging-redis-password-not-for-logs'
readonly CURRENT_SHA='0123456789abcdef0123456789abcdef01234567'

mkdir -p "$FIXTURE_SOURCE" "$FAKE_BIN"
printf 'fixture source\n' > "$FIXTURE_SOURCE/fixture-marker"
printf 'REDIS_PASSWORD=%s\nUNRELATED_SECRET=must-not-be-read\n' "$REDIS_SECRET" > "$FIXTURE_SOURCE/.env"
printf 'ref=main\ncommit=%s\ndeployed_at=2026-09-10T10:11:12Z\n---\n' "$CURRENT_SHA" > "$DEPLOY_HISTORY"

sed \
    -e "s@^readonly SOURCE_ROOT=/opt/bytedepth\$@readonly SOURCE_ROOT=$FIXTURE_SOURCE@" \
    -e "s@^readonly CONFIG_FILE=/etc/bytedepth-deploy.conf\$@readonly CONFIG_FILE=$FIXTURE_CONFIG@" \
    -e "s@^readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history\$@readonly EVIDENCE_DIR=$EVIDENCE_DIR@" \
    -e "s@^readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history\$@readonly DEPLOY_HISTORY=$DEPLOY_HISTORY@" \
    -e '/^if \[\[ "${EUID}" -ne 0 \]\]; then$/,/^fi$/d' \
    "$RUNNER" > "$TEMP_ROOT/runner"
chmod +x "$TEMP_ROOT/runner"

cat > "$FAKE_BIN/sudo" <<'SCRIPT'
#!/usr/bin/env bash
exec "$@"
SCRIPT
chmod +x "$FAKE_BIN/sudo"

cat > "$FAKE_BIN/docker" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STAGING_RUNNER_DOCKER_ARGS"

workspace=''
for argument in "$@"; do
    case "$argument" in
        *:/workspace)
            workspace="${argument%:/workspace}"
            ;;
    esac
done

[[ -n "$workspace" && -f "$workspace/fixture-marker" ]]
[[ "$workspace" != "$STAGING_RUNNER_SOURCE" ]]
[[ -s "$STAGING_RUNNER_GIT_LOG" ]]
mkdir -p "$workspace/target"
printf 'container write\n' > "$workspace/target/container-write"
env_file=''
previous=''
for argument in "$@"; do
    if [[ "$previous" == '--env-file' ]]; then
        env_file="$argument"
        break
    fi
    previous="$argument"
done
[[ -n "$env_file" && -f "$env_file" ]]
grep -Fqx "BYTEDEPTH_IT_REDIS_PASSWORD=$STAGING_RUNNER_REDIS_SECRET" "$env_file"
printf '%s\n' "${STAGING_RUNNER_DOCKER_OUTPUT:-Maven integration test output}"
exit "${STAGING_RUNNER_DOCKER_EXIT:-0}"
SCRIPT
chmod +x "$FAKE_BIN/docker"

cat > "$FAKE_BIN/git" <<'SCRIPT'
#!/usr/bin/env bash
printf 'git %s\n' "$*" >> "$STAGING_RUNNER_GIT_LOG"
if [[ "$*" == *'rev-parse HEAD'* ]]; then
    count_file="$STAGING_RUNNER_GIT_COUNT"
    count=0
    [[ -f "$count_file" ]] && count="$(cat "$count_file")"
    count=$((count + 1))
    printf '%s\n' "$count" > "$count_file"
    if [[ "$count" -eq 1 ]]; then
        printf '%s\n' "$STAGING_RUNNER_SHA"
    else
        printf '%s\n' "${STAGING_RUNNER_SHA_AFTER:-$STAGING_RUNNER_SHA}"
    fi
    exit 0
fi
exit 1
SCRIPT
chmod +x "$FAKE_BIN/git"

cat > "$FAKE_BIN/install" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$STAGING_RUNNER_INSTALL_ARGS"
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
    printf 'UNRELATED_CONFIG=must-not-be-read\nBYTEDEPTH_DEPLOY_MODE=%s\n' "$1" > "$FIXTURE_CONFIG"
}

run_runner() {
    PATH="$FAKE_BIN:$PATH" \
        STAGING_RUNNER_DOCKER_ARGS="$DOCKER_ARGS" \
        STAGING_RUNNER_SOURCE="$FIXTURE_SOURCE" \
        STAGING_RUNNER_GIT_LOG="$GIT_LOG" \
        STAGING_RUNNER_GIT_COUNT="$TEMP_ROOT/git.count" \
        STAGING_RUNNER_INSTALL_ARGS="$INSTALL_ARGS" \
        STAGING_RUNNER_SHA="$CURRENT_SHA" \
        STAGING_RUNNER_REDIS_SECRET="$REDIS_SECRET" \
        STAGING_RUNNER_DOCKER_OUTPUT="${STAGING_RUNNER_DOCKER_OUTPUT:-}" \
        STAGING_RUNNER_DOCKER_EXIT="${STAGING_RUNNER_DOCKER_EXIT:-0}" \
        "$TEMP_ROOT/runner" > "$RUNNER_OUTPUT" 2>&1
}

# A non-staging host must be rejected before Docker can run.
write_config single-host
if run_runner; then
    printf 'Expected runner to reject a non-staging deployment mode.\n' >&2
    exit 1
fi
[[ ! -e "$DOCKER_ARGS" ]]

# The happy path uses an isolated copy and Redis only through service DNS.
write_config staging
run_runner

grep -Fqx 'run' "$DOCKER_ARGS"
grep -Fqx -- '--rm' "$DOCKER_ARGS"
grep -Fqx -- '--network' "$DOCKER_ARGS"
grep -Fqx 'bytedepth_default' "$DOCKER_ARGS"
grep -Fqx -- '-Dbytedepth.it.redis.host=redis' "$DOCKER_ARGS"
grep -Fqx -- '-Dbytedepth.it.redis.port=6379' "$DOCKER_ARGS"
grep -Fqx -- '--env-file' "$DOCKER_ARGS"
! grep -Fq "$REDIS_SECRET" "$DOCKER_ARGS"
grep -Fqx -- '-Pstaging-integration' "$DOCKER_ARGS"
grep -Fqx 'verify' "$DOCKER_ARGS"
grep -Fq '/source:/workspace' "$DOCKER_ARGS"
! grep -Fq "$FIXTURE_SOURCE:/workspace" "$DOCKER_ARGS"
[[ ! -e "$FIXTURE_SOURCE/target/container-write" ]]
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"
! grep -Fq 'UNRELATED_SECRET' "$RUNNER_OUTPUT"
grep -Fqx "commit=$CURRENT_SHA" "$EVIDENCE_DIR/staging-integration"
grep -Fqx 'command=run-staging-integration-tests' "$EVIDENCE_DIR/staging-integration"
grep -Eq '^timestamp=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' "$EVIDENCE_DIR/staging-integration"
grep -Fqx 'result=passed' "$EVIDENCE_DIR/staging-integration"
grep -Fqx -- '-o' "$INSTALL_ARGS"
grep -Fqx 'root' "$INSTALL_ARGS"

# Even a dependency failure that includes the password must be redacted before tee writes output.
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
STAGING_RUNNER_DOCKER_OUTPUT="Maven connection detail $REDIS_SECRET" run_runner
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"
grep -Fq '[REDACTED]' "$RUNNER_OUTPUT"
[[ -e "$EVIDENCE_DIR/staging-integration" ]]

# A failed container invalidates an earlier pass and must not expose the credential.
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
if STAGING_RUNNER_DOCKER_EXIT=17 run_runner; then
    printf 'Expected runner to reject a failed Maven container.\n' >&2
    exit 1
fi
grep -Fq 'Staging integration tests failed.' "$RUNNER_OUTPUT"
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-integration" ]]

# A blank REDIS_PASSWORD is not a test credential and must fail before Docker starts.
printf 'REDIS_PASSWORD=   \nUNRELATED_SECRET=must-not-be-read\n' > "$FIXTURE_SOURCE/.env"
rm -f "$DOCKER_ARGS"
if run_runner; then
    printf 'Expected runner to reject a blank REDIS_PASSWORD.\n' >&2
    exit 1
fi
[[ ! -e "$DOCKER_ARGS" ]]

# Maven warnings are deployment-gate failures even when Docker exits zero.
printf 'REDIS_PASSWORD=%s\n' "$REDIS_SECRET" > "$FIXTURE_SOURCE/.env"
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
run_runner
[[ -e "$EVIDENCE_DIR/staging-integration" ]]
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
if STAGING_RUNNER_DOCKER_OUTPUT='WARNING: simulated Maven warning' run_runner; then
    printf 'Expected runner to reject Maven warning output.\n' >&2
    exit 1
fi
grep -Fq 'WARNING: simulated Maven warning' "$RUNNER_OUTPUT"
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-integration" ]]

# The deployed checkout must not advance while the isolated Maven copy runs.
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
if STAGING_RUNNER_SHA_AFTER=ffffffffffffffffffffffffffffffffffffffff run_runner; then
    printf 'Expected runner to reject a changed checkout after integration tests.\n' >&2
    exit 1
fi
grep -Fq 'checked-out commit changed during staging integration tests' "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-integration" ]]

# A current checkout without a matching deployed-app record is never evidence for the running app.
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
run_runner
[[ -e "$EVIDENCE_DIR/staging-integration" ]]
printf 'ref=main\ncommit=ffffffffffffffffffffffffffffffffffffffff\ndeployed_at=2026-09-10T10:11:12Z\n---\n' > "$DEPLOY_HISTORY"
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count"
if run_runner; then
    printf 'Expected runner to reject an app deployment SHA different from the checkout.\n' >&2
    exit 1
fi
grep -Fq 'staging app deployment does not match the tested checkout commit' "$RUNNER_OUTPUT"
[[ ! -e "$DOCKER_ARGS" ]]
[[ ! -e "$EVIDENCE_DIR/staging-integration" ]]

# Static safety invariants: no host publishing, loopback, or production address.
! grep -Fq -- '--publish' "$RUNNER"
! grep -Fqi 'localhost' "$RUNNER"
! grep -Eq '175\.24\.197\.202|10\.0\.4\.15|bytedepth\.cn' "$RUNNER"

printf 'staging integration runner tests passed\n'
