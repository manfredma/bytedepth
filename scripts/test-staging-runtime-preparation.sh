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
printf 'staging runtime preparation tests passed\n'
