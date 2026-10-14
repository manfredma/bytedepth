#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# Use the real manifest validator with deterministic browser/version fixtures.
mkdir -p "$tmp/source/deploy" "$tmp/runtime"
printf 'lock inputs\n' > "$tmp/source/package-lock.json"
printf 'pom inputs\n' > "$tmp/source/pom.xml"
# The Chromium path is readonly; transform only this fixture's library path.
sed "s|/opt/shared-e2e/chrome-linux64/chrome|$tmp/chrome|g" "$root/deploy/lib/staging-runtime.sh" > "$tmp/runtime-lib.sh"
cat > "$tmp/chrome" << 'CHROME'
#!/usr/bin/env bash
printf 'Chromium fixture\n'
CHROME
chmod +x "$tmp/chrome"
cat > "$tmp/source/deploy/bootstrap-staging-runtime.sh" << 'BOOTSTRAP'
#!/usr/bin/env bash
set -Eeuo pipefail
[[ "$*" == '--lock-held --ensure' ]]
[[ "$RELEASE_PLATFORM_TEST_SOURCE_ROOT" == "$EXPECTED_SOURCE" ]]
[[ "$RELEASE_PLATFORM_TEST_RUNTIME_MANIFEST" == "$EXPECTED_MANIFEST" ]]
printf 'prepared\n' >> "$PREPARATION_CALLS"
[[ "${FAIL_PREPARATION:-0}" == 0 ]] || exit 7
[[ "${SKIP_STAMP:-0}" == 0 ]] || exit 0
source "$FIXTURE_RUNTIME_LIB"
staging_shared_ffmpeg_version() { printf 'ffmpeg fixture\n'; }
write_runtime_manifest "$RELEASE_PLATFORM_TEST_RUNTIME_MANIFEST" "$RELEASE_PLATFORM_TEST_SOURCE_ROOT"
BOOTSTRAP
chmod +x "$tmp/source/deploy/bootstrap-staging-runtime.sh"
export EXPECTED_SOURCE="$tmp/source" EXPECTED_MANIFEST="$tmp/runtime/manifest"
export PREPARATION_CALLS="$tmp/calls" FIXTURE_RUNTIME_LIB="$tmp/runtime-lib.sh"
# Invoke in a fresh shell so readonly runtime constants remain untouched.
cat > "$tmp/run" << 'RUN'
set -Eeuo pipefail
source "$FIXTURE_RUNTIME_LIB"
staging_shared_ffmpeg_version() { printf 'ffmpeg fixture\n'; }
if declare -F ensure_staging_runtime > /dev/null; then
  ensure_staging_runtime "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"
else
  require_staging_runtime "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"
fi
RUN
printf 'package_lock_sha256=stale\npom_sha256=stale\n' > "$EXPECTED_MANIFEST"
bash "$tmp/run"
[[ "$(wc -l < "$PREPARATION_CALLS" | tr -d ' ')" == 1 ]]
bash "$tmp/run"
[[ "$(wc -l < "$PREPARATION_CALLS" | tr -d ' ')" == 1 ]]
printf 'changed dependency\n' >> "$tmp/source/pom.xml"
if FAIL_PREPARATION=1 bash "$tmp/run"; then
  printf 'Failed preparation accepted\n' >&2
  exit 1
fi
if SKIP_STAMP=1 bash "$tmp/run"; then
  printf 'Preparation without a current manifest accepted\n' >&2
  exit 1
fi
# A failed preparation must not stamp a manifest for new inputs.
if bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_shared_ffmpeg_version() { printf "ffmpeg fixture\n"; }; require_staging_runtime "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"'; then
  printf 'Failed preparation stamped new inputs\n' >&2
  exit 1
fi
# Exercise the existing bootstrap's real --ensure path with a deployed source
# snapshot (no .git), while replacing only privileged host fixture paths.
mkdir -p "$tmp/source/deploy/lib"
cp "$tmp/runtime-lib.sh" "$tmp/source/deploy/lib/staging-runtime.sh"
# Supply the fixture ffmpeg version through the actual library's helper.
cat >> "$tmp/source/deploy/lib/staging-runtime.sh" << 'LIB'
staging_shared_ffmpeg_version() { printf 'ffmpeg fixture\n'; }
LIB
cp "$root/deploy/lib/timing.sh" "$root/deploy/lib/warning-policy.sh" "$root/deploy/lib/staging-platform-contract.sh" "$tmp/source/deploy/lib/"
cp "$root/deploy/bootstrap-staging-runtime.sh" "$tmp/bootstrap-real.sh"
python3 - "$tmp/bootstrap-real.sh" "$tmp/maven" << 'PY_FIXTURE'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text().replace('if [[ "${EUID}" -ne 0 ]]; then', 'if false; then')
s = s.replace('readonly SHARED_MAVEN_REPOSITORY=/opt/shared-maven/repository', 'readonly SHARED_MAVEN_REPOSITORY=' + sys.argv[2])
p.write_text(s)
PY_FIXTURE
mkdir -p "$tmp/maven"
printf 'BYTEDEPTH_DEPLOY_MODE=staging\n' > "$tmp/config"
printf '0123456789abcdef0123456789abcdef01234567\n' > "$tmp/source/.release-platform-source-commit"
bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_shared_ffmpeg_version() { printf "ffmpeg fixture\n"; }; write_runtime_manifest "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"'
RELEASE_PLATFORM_TEST_SOURCE_ROOT="$EXPECTED_SOURCE" \
  RELEASE_PLATFORM_TEST_RUNTIME_MANIFEST="$EXPECTED_MANIFEST" \
  RELEASE_PLATFORM_TEST_CONFIG_FILE="$tmp/config" \
  RELEASE_PLATFORM_TEST_WORK_DIR="$tmp/state" \
  bash "$tmp/bootstrap-real.sh" --lock-held --ensure
for phase in integration e2e; do
  grep -Fq 'ensure_staging_runtime "$RUNTIME_MANIFEST" "$SOURCE_ROOT"' "$root/deploy/run-staging-$phase-tests.sh"
done

# Run the actual bootstrap log collectors with offline commands. A logging sink
# failure must fail preparation even when Maven itself succeeds, and private
# mktemp logs must retain the creating process's ownership while it writes them.
mkdir -p "$tmp/log-fixture/bin"
python3 - "$root/deploy/bootstrap-staging-runtime.sh" "$tmp/log-fixture/collectors.sh" << 'PY'
from pathlib import Path
import re, sys
source = Path(sys.argv[1]).read_text()
functions = [re.search(r'^' + name + r'\(\) \{\n.*?^\}', source, re.M | re.S).group(0)
             for name in ['prepare_maven', 'prepare_playwright_runtime']]
Path(sys.argv[2]).write_text('\n'.join(functions) + '\n')
PY
cat > "$tmp/log-fixture/bin/flock" << 'SH_FLOCK'
#!/usr/bin/env bash
exit 0
SH_FLOCK
cat > "$tmp/log-fixture/bin/install" << 'SH_INSTALL'
#!/usr/bin/env bash
mkdir -p "${@: -1}"
SH_INSTALL
cat > "$tmp/log-fixture/bin/tee" << 'SH_TEE'
#!/usr/bin/env bash
if [[ "${FAIL_LOG_SINK:-0}" == 1 ]]; then
  cat > /dev/null
  printf 'fixture: log sink permission denied\n' >&2
  exit 73
fi
exec /usr/bin/tee "$@"
SH_TEE
cat > "$tmp/log-fixture/mvnw" << 'SH_MAVEN'
#!/usr/bin/env bash
printf 'Maven fixture succeeded\n'
if [[ "${EMIT_WARNING:-0}" == 1 ]]; then printf '[WARNING] unexpected fixture warning\n'; fi
exit "${COMMAND_EXIT:-0}"
SH_MAVEN
cp "$tmp/log-fixture/mvnw" "$tmp/log-fixture/bin/npm"
chmod +x "$tmp/log-fixture/bin/"* "$tmp/log-fixture/mvnw"
cat > "$tmp/log-fixture/run" << 'SH_RUN'
set -Eeuo pipefail
source "$ROOT/deploy/lib/warning-policy.sh"
SOURCE_ROOT="$LOG_FIXTURE"
SHARED_MAVEN_REPOSITORY="$LOG_FIXTURE/repository"
SHARED_MAVEN_LOCK="$LOG_FIXTURE/repository.lock"
staging_ensure_ubuntu_owner() { printf '%s\n' "$@" >> "$LOG_FIXTURE/ownership-changes"; }
source "$LOG_FIXTURE/collectors.sh"
"$COLLECTOR"
SH_RUN
export ROOT="$root" LOG_FIXTURE="$tmp/log-fixture"
for collector in prepare_maven prepare_playwright_runtime; do
  COLLECTOR="$collector" PATH="$tmp/log-fixture/bin:$PATH" bash "$tmp/log-fixture/run"
  if COLLECTOR="$collector" COMMAND_EXIT=17 PATH="$tmp/log-fixture/bin:$PATH" bash "$tmp/log-fixture/run"; then
    printf 'Bootstrap accepted a failed preparation command: %s\n' "$collector" >&2
    exit 1
  else
    status="$?"
    [[ "$status" == 17 ]] || {
      printf 'Bootstrap lost the command exit code: %s\n' "$status" >&2
      exit 1
    }
  fi
  if COLLECTOR="$collector" FAIL_LOG_SINK=1 PATH="$tmp/log-fixture/bin:$PATH" bash "$tmp/log-fixture/run"; then
    printf 'Bootstrap accepted a failed log sink: %s\n' "$collector" >&2
    exit 1
  fi
  if COLLECTOR="$collector" EMIT_WARNING=1 PATH="$tmp/log-fixture/bin:$PATH" bash "$tmp/log-fixture/run"; then
    printf 'Bootstrap accepted an unallowlisted warning: %s\n' "$collector" >&2
    exit 1
  fi
done
if [[ -e "$tmp/log-fixture/ownership-changes" ]]; then
  printf 'Bootstrap changed a private log owner before writing it\n' >&2
  exit 1
fi
printf 'staging runtime preparation tests passed\n'
printf 'staging bootstrap log capture tests passed\n'
