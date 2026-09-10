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
readonly FAKE_BIN="$TEMP_ROOT/bin"
readonly DOCKER_ARGS="$TEMP_ROOT/docker.args"
readonly RUNNER_OUTPUT="$TEMP_ROOT/runner.out"
readonly REDIS_SECRET='staging-redis-password-not-for-logs'

mkdir -p "$FIXTURE_SOURCE" "$FAKE_BIN"
printf 'fixture source\n' > "$FIXTURE_SOURCE/fixture-marker"
printf 'REDIS_PASSWORD=%s\nUNRELATED_SECRET=must-not-be-read\n' "$REDIS_SECRET" > "$FIXTURE_SOURCE/.env"

sed \
    -e "s@^readonly SOURCE_ROOT=/opt/bytedepth\$@readonly SOURCE_ROOT=$FIXTURE_SOURCE@" \
    -e "s@^readonly CONFIG_FILE=/etc/bytedepth-deploy.conf\$@readonly CONFIG_FILE=$FIXTURE_CONFIG@" \
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

write_config() {
    printf 'UNRELATED_CONFIG=must-not-be-read\nBYTEDEPTH_DEPLOY_MODE=%s\n' "$1" > "$FIXTURE_CONFIG"
}

run_runner() {
    PATH="$FAKE_BIN:$PATH" \
        STAGING_RUNNER_DOCKER_ARGS="$DOCKER_ARGS" \
        STAGING_RUNNER_SOURCE="$FIXTURE_SOURCE" \
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

# A failed container must make the runner fail without echoing the credential.
rm -f "$DOCKER_ARGS"
if STAGING_RUNNER_DOCKER_EXIT=17 run_runner; then
    printf 'Expected runner to reject a failed Maven container.\n' >&2
    exit 1
fi
grep -Fq 'Staging integration tests failed.' "$RUNNER_OUTPUT"
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"

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
rm -f "$DOCKER_ARGS"
if STAGING_RUNNER_DOCKER_OUTPUT='WARNING: simulated Maven warning' run_runner; then
    printf 'Expected runner to reject Maven warning output.\n' >&2
    exit 1
fi
grep -Fq 'WARNING: simulated Maven warning' "$RUNNER_OUTPUT"
! grep -Fq "$REDIS_SECRET" "$RUNNER_OUTPUT"

# Static safety invariants: no host publishing, loopback, or production address.
! grep -Fq -- '--publish' "$RUNNER"
! grep -Fqi 'localhost' "$RUNNER"
! grep -Eq '175\.24\.197\.202|10\.0\.4\.15|bytedepth\.cn' "$RUNNER"

printf 'staging integration runner tests passed\n'
