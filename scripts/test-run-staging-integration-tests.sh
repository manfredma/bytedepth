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

sed \
    -e "s@^readonly SOURCE_ROOT=/opt/bytedepth\$@readonly SOURCE_ROOT=$FIXTURE_SOURCE@" \
    -e "s@^readonly CONFIG_FILE=/etc/bytedepth-deploy.conf\$@readonly CONFIG_FILE=$FIXTURE_CONFIG@" \
    -e "s@^readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history\$@readonly EVIDENCE_DIR=$EVIDENCE_DIR@" \
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
mkdir -p "$workspace/target"
printf 'container write\n' > "$workspace/target/container-write"
printf '%s\n' "${STAGING_RUNNER_DOCKER_OUTPUT:-Maven integration test output}"
exit "${STAGING_RUNNER_DOCKER_EXIT:-0}"
SCRIPT
chmod +x "$FAKE_BIN/docker"

cat > "$FAKE_BIN/git" <<'SCRIPT'
#!/usr/bin/env bash
printf 'git %s\n' "$*" >> "$STAGING_RUNNER_GIT_LOG"
if [[ "$*" == *'rev-parse HEAD'* ]]; then
    printf '%s\n' "$STAGING_RUNNER_SHA"
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
        STAGING_RUNNER_INSTALL_ARGS="$INSTALL_ARGS" \
        STAGING_RUNNER_SHA="$CURRENT_SHA" \
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
grep -Fqx -- "-Dbytedepth.it.redis.password=$REDIS_SECRET" "$DOCKER_ARGS"
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

# A failed container must make the runner fail without echoing the credential.
rm -f "$DOCKER_ARGS" "$EVIDENCE_DIR/staging-integration" "$GIT_LOG"
if STAGING_RUNNER_DOCKER_EXIT=17 run_runner; then
    printf 'Expected runner to reject a failed Maven container.\n' >&2
    exit 1
fi
grep -Fq 'Staging integration tests failed.' "$RUNNER_OUTPUT"
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-integration" ]]
[[ ! -e "$GIT_LOG" ]]

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
rm -f "$DOCKER_ARGS" "$EVIDENCE_DIR/staging-integration" "$GIT_LOG"
if STAGING_RUNNER_DOCKER_OUTPUT='WARNING: simulated Maven warning' run_runner; then
    printf 'Expected runner to reject Maven warning output.\n' >&2
    exit 1
fi
grep -Fq 'WARNING: simulated Maven warning' "$RUNNER_OUTPUT"
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"
[[ ! -e "$EVIDENCE_DIR/staging-integration" ]]
[[ ! -e "$GIT_LOG" ]]

# Static safety invariants: no host publishing, loopback, or production address.
! grep -Fq -- '--publish' "$RUNNER"
! grep -Fqi 'localhost' "$RUNNER"
! grep -Eq '175\.24\.197\.202|10\.0\.4\.15|bytedepth\.cn' "$RUNNER"

printf 'staging integration runner tests passed\n'
