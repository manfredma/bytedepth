#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly RUNNER="$SOURCE_ROOT/deploy/run-staging-integration-tests.sh"
readonly DOCKERFILE="$SOURCE_ROOT/Dockerfile"
TEMP_ROOT="$(mktemp -d)"
readonly TEMP_ROOT
trap 'rm -rf "$TEMP_ROOT"' EXIT

if [[ ! -f "$RUNNER" ]]; then
    printf 'Expected staging integration runner at %s\n' "$RUNNER" >&2
    exit 1
fi

# Both staging Maven execution contexts use Tencent Cloud's public mirror.
# These checks intentionally fail until the production configuration is
# switched; they also prevent a future fallback to the former Aliyun mirror.
! grep -Fq 'https://maven.aliyun.com/repository/public' "$DOCKERFILE"
grep -Fqx '      <id>tencent-cloud</id>' "$DOCKERFILE"
grep -Fqx '      <url>https://mirrors.tencent.com/nexus/repository/maven-public/</url>' "$DOCKERFILE"
grep -Fqx '      <mirrorOf>*</mirrorOf>' "$DOCKERFILE"

readonly FIXTURE_ROOT="$TEMP_ROOT/fixture"
readonly FIXTURE_SOURCE="$FIXTURE_ROOT/source"
readonly FIXTURE_CONFIG="$FIXTURE_ROOT/bytedepth-deploy.conf"
readonly EVIDENCE_DIR="$FIXTURE_ROOT/test-history"
readonly DEPLOY_HISTORY="$FIXTURE_ROOT/deploy-history"
readonly LOCK_FILE="$FIXTURE_ROOT/deployment-test.lock"
readonly DOCKER_SOCKET="$FIXTURE_ROOT/docker.sock"
readonly FAKE_BIN="$TEMP_ROOT/bin"
readonly DOCKER_ARGS="$TEMP_ROOT/docker.args"
readonly DOCKER_INFO_ARGS="$TEMP_ROOT/docker-info.args"
readonly FLOCK_ARGS="$TEMP_ROOT/flock.args"
readonly GIT_LOG="$TEMP_ROOT/git.log"
readonly INSTALL_ARGS="$TEMP_ROOT/install.args"
readonly RUNNER_OUTPUT="$TEMP_ROOT/runner.out"
readonly REDIS_SECRET='staging-redis-password-not-for-logs'
readonly CURRENT_SHA='0123456789abcdef0123456789abcdef01234567'

mkdir -p "$FIXTURE_SOURCE" "$FAKE_BIN"
printf 'fixture source\n' > "$FIXTURE_SOURCE/fixture-marker"
printf 'fixture Docker socket placeholder\n' > "$DOCKER_SOCKET"
printf 'REDIS_PASSWORD=%s\nUNRELATED_SECRET=must-not-be-read\n' "$REDIS_SECRET" > "$FIXTURE_SOURCE/.env"
printf 'ref=main\ncommit=%s\ndeployed_at=2026-09-10T10:11:12Z\n---\n' "$CURRENT_SHA" > "$DEPLOY_HISTORY"

sed \
    -e "s@^readonly SOURCE_ROOT=/opt/bytedepth\$@readonly SOURCE_ROOT=$FIXTURE_SOURCE@" \
    -e "s@^readonly CONFIG_FILE=/etc/bytedepth-deploy.conf\$@readonly CONFIG_FILE=$FIXTURE_CONFIG@" \
    -e "s@^readonly EVIDENCE_DIR=/var/lib/bytedepth-staging/test-history\$@readonly EVIDENCE_DIR=$EVIDENCE_DIR@" \
    -e "s@^readonly DEPLOY_HISTORY=/var/lib/bytedepth-staging/deploy-history\$@readonly DEPLOY_HISTORY=$DEPLOY_HISTORY@" \
    -e "s@^readonly LOCK_FILE=/var/lib/bytedepth-staging/deployment-test.lock\$@readonly LOCK_FILE=$LOCK_FILE@" \
    -e "s@^readonly DOCKER_SOCKET=/var/run/docker.sock\$@readonly DOCKER_SOCKET=$DOCKER_SOCKET@" \
    -e '/^if \[\[ "${EUID}" -ne 0 \]\]; then$/,/^fi$/d' \
    "$RUNNER" > "$TEMP_ROOT/runner"
chmod +x "$TEMP_ROOT/runner"

cat > "$FAKE_BIN/sudo" <<'SCRIPT'
#!/usr/bin/env bash
exec "$@"
SCRIPT
chmod +x "$FAKE_BIN/sudo"

cat > "$FAKE_BIN/flock" <<'SCRIPT'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$STAGING_RUNNER_FLOCK_ARGS"
[[ "$1" == '-x' ]]
[[ "$2" == "$STAGING_RUNNER_LOCK_FILE" ]]
lock_dir="$2.test-lock"
shift 2
[[ "$1" == *runner ]]
until mkdir "$lock_dir" 2>/dev/null; do
    sleep 0.01
done
"$@"
result=$?
rmdir "$lock_dir"
exit "$result"
SCRIPT
chmod +x "$FAKE_BIN/flock"

cat > "$FAKE_BIN/docker" <<'SCRIPT'
#!/usr/bin/env bash
if [[ "${1:-}" == '-H' && "${3:-}" == 'info' ]]; then
    printf '%s\n' "$@" > "$STAGING_RUNNER_DOCKER_INFO_ARGS"
    [[ "$2" == "unix://$STAGING_RUNNER_DOCKER_SOCKET" ]]
    [[ -e "$STAGING_RUNNER_DOCKER_SOCKET" ]]
    exit "${STAGING_RUNNER_DOCKER_INFO_EXIT:-0}"
fi

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
[[ ! -e "$workspace/.env" ]]
! grep -R -Fq 'UNRELATED_SECRET=must-not-be-read' "$workspace"
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
settings_file=''
for argument in "$@"; do
    case "$argument" in
        *:/root/.m2/settings.xml:ro)
            settings_file="${argument%:/root/.m2/settings.xml:ro}"
            ;;
    esac
done
[[ -n "$settings_file" && -f "$settings_file" ]]
grep -Fqx '      <id>tencent-cloud</id>' "$settings_file"
grep -Fqx '      <url>https://mirrors.tencent.com/nexus/repository/maven-public/</url>' "$settings_file"
grep -Fqx '      <mirrorOf>*</mirrorOf>' "$settings_file"
if [[ -n "${STAGING_RUNNER_DOCKER_STARTED_FILE:-}" ]]; then
    printf 'started\n' >> "$STAGING_RUNNER_DOCKER_STARTED_FILE"
fi
if [[ -n "${STAGING_RUNNER_DOCKER_RELEASE_FILE:-}" ]]; then
    while [[ ! -e "$STAGING_RUNNER_DOCKER_RELEASE_FILE" ]]; do
        sleep 0.01
    done
fi
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
        STAGING_RUNNER_DOCKER_INFO_ARGS="$DOCKER_INFO_ARGS" \
        STAGING_RUNNER_DOCKER_SOCKET="$DOCKER_SOCKET" \
        STAGING_RUNNER_FLOCK_ARGS="$FLOCK_ARGS" \
        STAGING_RUNNER_LOCK_FILE="$LOCK_FILE" \
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

grep -Fqx -- '-x' "$FLOCK_ARGS"
grep -Fqx "$LOCK_FILE" "$FLOCK_ARGS"

grep -Fqx 'run' "$DOCKER_ARGS"
grep -Fqx -- '--rm' "$DOCKER_ARGS"
grep -Fqx -- '--network' "$DOCKER_ARGS"
grep -Fqx 'bytedepth_default' "$DOCKER_ARGS"
grep -Fqx -- '--add-host' "$DOCKER_ARGS"
grep -Fqx 'host.docker.internal:host-gateway' "$DOCKER_ARGS"
grep -Fqx -- '--env' "$DOCKER_ARGS"
grep -Fqx 'TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal' "$DOCKER_ARGS"
grep -Fqx -- '-v' "$DOCKER_ARGS"
grep -Fqx "$DOCKER_SOCKET:$DOCKER_SOCKET" "$DOCKER_ARGS"
grep -Fqx -- '-H' "$DOCKER_INFO_ARGS"
grep -Fqx "unix://$DOCKER_SOCKET" "$DOCKER_INFO_ARGS"
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

# The second test transaction cannot reach Maven until the first transaction
# releases the shared deployment/test lock.
readonly FIRST_OUTPUT="$TEMP_ROOT/first-runner.out"
readonly SECOND_OUTPUT="$TEMP_ROOT/second-runner.out"
readonly STARTED_FILE="$TEMP_ROOT/docker-started"
readonly RELEASE_FILE="$TEMP_ROOT/docker-release"
rm -f "$DOCKER_ARGS" "$GIT_LOG" "$TEMP_ROOT/git.count" "$STARTED_FILE" "$RELEASE_FILE"
PATH="$FAKE_BIN:$PATH" \
    STAGING_RUNNER_DOCKER_ARGS="$DOCKER_ARGS" \
    STAGING_RUNNER_DOCKER_INFO_ARGS="$DOCKER_INFO_ARGS" \
    STAGING_RUNNER_DOCKER_SOCKET="$DOCKER_SOCKET" \
    STAGING_RUNNER_FLOCK_ARGS="$FLOCK_ARGS" \
    STAGING_RUNNER_LOCK_FILE="$LOCK_FILE" \
    STAGING_RUNNER_SOURCE="$FIXTURE_SOURCE" \
    STAGING_RUNNER_GIT_LOG="$GIT_LOG" \
    STAGING_RUNNER_GIT_COUNT="$TEMP_ROOT/git.count" \
    STAGING_RUNNER_INSTALL_ARGS="$INSTALL_ARGS" \
    STAGING_RUNNER_SHA="$CURRENT_SHA" \
    STAGING_RUNNER_REDIS_SECRET="$REDIS_SECRET" \
    STAGING_RUNNER_DOCKER_STARTED_FILE="$STARTED_FILE" \
    STAGING_RUNNER_DOCKER_RELEASE_FILE="$RELEASE_FILE" \
    "$TEMP_ROOT/runner" > "$FIRST_OUTPUT" 2>&1 &
first_pid=$!
for _ in {1..100}; do
    [[ -e "$STARTED_FILE" ]] && break
    sleep 0.01
done
[[ -e "$STARTED_FILE" ]]
PATH="$FAKE_BIN:$PATH" \
    STAGING_RUNNER_DOCKER_ARGS="$DOCKER_ARGS" \
    STAGING_RUNNER_DOCKER_INFO_ARGS="$DOCKER_INFO_ARGS" \
    STAGING_RUNNER_DOCKER_SOCKET="$DOCKER_SOCKET" \
    STAGING_RUNNER_FLOCK_ARGS="$FLOCK_ARGS" \
    STAGING_RUNNER_LOCK_FILE="$LOCK_FILE" \
    STAGING_RUNNER_SOURCE="$FIXTURE_SOURCE" \
    STAGING_RUNNER_GIT_LOG="$GIT_LOG" \
    STAGING_RUNNER_GIT_COUNT="$TEMP_ROOT/git.count" \
    STAGING_RUNNER_INSTALL_ARGS="$INSTALL_ARGS" \
    STAGING_RUNNER_SHA="$CURRENT_SHA" \
    STAGING_RUNNER_REDIS_SECRET="$REDIS_SECRET" \
    STAGING_RUNNER_DOCKER_STARTED_FILE="$STARTED_FILE" \
    "$TEMP_ROOT/runner" > "$SECOND_OUTPUT" 2>&1 &
second_pid=$!
sleep 0.1
[[ "$(wc -l < "$STARTED_FILE")" -eq 1 ]]
touch "$RELEASE_FILE"
wait "$first_pid"
wait "$second_pid"

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

# Testcontainers needs a usable Docker daemon socket.  A missing socket must
# be rejected before the disposable Maven container starts.
printf 'REDIS_PASSWORD=%s\n' "$REDIS_SECRET" > "$FIXTURE_SOURCE/.env"
rm -f "$DOCKER_SOCKET" "$DOCKER_ARGS" "$DOCKER_INFO_ARGS"
if run_runner; then
    printf 'Expected runner to reject a missing Docker socket.\n' >&2
    exit 1
fi
grep -Fq 'Docker daemon socket is unavailable' "$RUNNER_OUTPUT"
[[ ! -e "$DOCKER_ARGS" ]]
[[ ! -e "$DOCKER_INFO_ARGS" ]]
printf 'fixture Docker socket placeholder\n' > "$DOCKER_SOCKET"

# A path alone is insufficient: the runner must verify that the daemon behind
# the socket can answer before Maven starts.
rm -f "$DOCKER_ARGS" "$DOCKER_INFO_ARGS"
if STAGING_RUNNER_DOCKER_INFO_EXIT=1 run_runner; then
    printf 'Expected runner to reject an unusable Docker socket.\n' >&2
    exit 1
fi
grep -Fq 'Docker daemon socket is not usable' "$RUNNER_OUTPUT"
[[ -e "$DOCKER_INFO_ARGS" ]]
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
