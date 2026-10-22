#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
# Use the real manifest validator with deterministic browser/version fixtures.
mkdir -p "$tmp/source/deploy" "$tmp/source/bytedepth-start" "$tmp/runtime"
printf 'lock inputs\n' > "$tmp/source/package-lock.json"
printf '<project><modules><module>bytedepth-start</module></modules></project>\n' > "$tmp/source/pom.xml"
printf '<project/>\n' > "$tmp/source/bytedepth-start/pom.xml"
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
# A new module dependency must invalidate a root-POM-identical manifest.
printf '<!-- new session starter -->\n' >> "$tmp/source/bytedepth-start/pom.xml"
if FAIL_PREPARATION=1 bash "$tmp/run"; then
  printf 'Module POM change reused a stale staging runtime manifest\n' >&2
  exit 1
fi
# Successful preparation stamps the new complete input set, then reuse is safe.
bash "$tmp/run"
[[ "$(wc -l < "$PREPARATION_CALLS" | tr -d ' ')" == 3 ]]
bash "$tmp/run"
[[ "$(wc -l < "$PREPARATION_CALLS" | tr -d ' ')" == 3 ]]
# Validate the real no-.git manifest against config/wrapper and nested modules.
assert_maven_inputs_changed() {
  local reason="$1"
  if bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_shared_ffmpeg_version() { printf "ffmpeg fixture\n"; }; require_staging_runtime "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"' > "$tmp/validation.out" 2>&1; then
    printf 'Stale staging runtime accepted after %s\n' "$reason" >&2
    exit 1
  fi
  grep -Fq 'maven_inputs recorded=' "$tmp/validation.out"
  bash "$tmp/run" > "$tmp/recovery.out" 2>&1
}
# An otherwise-current root-POM-only legacy manifest cannot certify module inputs.
sed '/^maven_inputs_sha256=/d' "$EXPECTED_MANIFEST" > "$tmp/runtime/legacy"
printf 'pom_sha256=%s\n' "$(shasum -a 256 "$tmp/source/pom.xml" | awk '{print $1}')" >> "$tmp/runtime/legacy"
if EXPECTED_MANIFEST="$tmp/runtime/legacy" FAIL_PREPARATION=1 bash "$tmp/run" > "$tmp/legacy.out" 2>&1; then
  printf 'Legacy root-POM-only manifest accepted\n' >&2
  exit 1
fi
grep -Fq 'maven_inputs recorded=' "$tmp/legacy.out"
for input in .mvn/settings.xml .mvn/maven.config .mvn/jvm.config .mvn/extensions.xml .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.jar mvnw mvnw.cmd; do
  mkdir -p "$(dirname "$tmp/source/$input")"
  printf 'first input version\n' > "$tmp/source/$input"
  assert_maven_inputs_changed "adding $input"
  printf 'changed input version\n' >> "$tmp/source/$input"
  assert_maven_inputs_changed "changing $input"
done
mv "$tmp/source/.mvn/extensions.xml" "$tmp/source/.mvn/renamed-extensions.xml"
assert_maven_inputs_changed 'renaming a Maven configuration file with unchanged bytes'
rm "$tmp/source/.mvn/renamed-extensions.xml"
assert_maven_inputs_changed 'deleting a Maven configuration file'
# Recursively include modules declared in any POM, including namespaced POMs.
printf '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>nested</module></modules></project>\n' > "$tmp/source/bytedepth-start/pom.xml"
mkdir -p "$tmp/source/bytedepth-start/nested"
printf '<project/>\n' > "$tmp/source/bytedepth-start/nested/pom.xml"
assert_maven_inputs_changed 'adding a nested reactor module'
printf '<!-- changed nested dependency -->\n' >> "$tmp/source/bytedepth-start/nested/pom.xml"
assert_maven_inputs_changed 'changing a nested module POM'
mv "$tmp/source/bytedepth-start/nested" "$tmp/source/bytedepth-start/renamed"
sed 's|<module>nested</module>|<module>renamed</module>|' "$tmp/source/bytedepth-start/pom.xml" > "$tmp/renamed-pom"
mv "$tmp/renamed-pom" "$tmp/source/bytedepth-start/pom.xml"
assert_maven_inputs_changed 'renaming a reactor module'
mv "$tmp/source/bytedepth-start/renamed/pom.xml" "$tmp/missing-pom"
if bash -c 'source "$FIXTURE_RUNTIME_LIB"; require_staging_runtime "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"' > "$tmp/missing.out" 2>&1; then
  printf 'Missing declared reactor POM accepted\n' >&2
  exit 1
fi
grep -Fq 'cannot fingerprint staging Maven dependency inputs:' "$tmp/missing.out"
manifest_before="$(shasum -a 256 "$EXPECTED_MANIFEST" | awk '{print $1}')"
if bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_shared_ffmpeg_version() { printf "ffmpeg fixture\n"; }; write_runtime_manifest "$EXPECTED_MANIFEST" "$EXPECTED_SOURCE"' > "$tmp/missing-write.out" 2>&1; then
  printf 'Missing declared reactor POM was stamped as prepared\n' >&2
  exit 1
fi
[[ "$(shasum -a 256 "$EXPECTED_MANIFEST" | awk '{print $1}')" == "$manifest_before" ]]
mv "$tmp/missing-pom" "$tmp/source/bytedepth-start/renamed/pom.xml"
printf '<project/>\n' > "$tmp/source/bytedepth-start/pom.xml"
rm -rf "$tmp/source/bytedepth-start/renamed"
assert_maven_inputs_changed 'removing a reactor module'
# Only project/profile module declarations are reactor inputs; similarly named
# plugin configuration must not be interpreted as a path to another POM.
printf '<project><build><plugins><plugin><configuration><modules><module>plugin-option</module></modules></configuration></plugin></plugins></build><profiles><profile><id>extra</id><modules><module>profiled</module></modules></profile></profiles></project>\n' > "$tmp/source/bytedepth-start/pom.xml"
mkdir -p "$tmp/source/bytedepth-start/profiled"
printf '<project/>\n' > "$tmp/source/bytedepth-start/profiled/pom.xml"
if ! bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_maven_inputs_sha256 "$EXPECTED_SOURCE"' > "$tmp/profile-hash.out" 2>&1; then
  printf 'Plugin configuration modules were mistaken for reactor declarations\n' >&2
  exit 1
fi
assert_maven_inputs_changed 'adding a profile reactor module without following plugin options'
printf '<!-- profile dependency -->\n' >> "$tmp/source/bytedepth-start/profiled/pom.xml"
assert_maven_inputs_changed 'changing a profile module POM'
# Generated/undeclared POMs do not change the reactor's dependency inputs.
for generated in node_modules/package target/generated android/build apps/mobile/node_modules/package; do
  mkdir -p "$tmp/source/$generated"
  printf '<project/>\n' > "$tmp/source/$generated/pom.xml"
done
calls_before="$(wc -l < "$PREPARATION_CALLS")"
bash "$tmp/run"
[[ "$(wc -l < "$PREPARATION_CALLS")" == "$calls_before" ]]
# The same tracked input paths/bytes give the same hash in a relocated snapshot.
cp -R "$tmp/source" "$tmp/relocated-source"
original_hash="$(bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_maven_inputs_sha256 "$1"' -- "$tmp/source")"
relocated_hash="$(bash -c 'source "$FIXTURE_RUNTIME_LIB"; staging_maven_inputs_sha256 "$1"' -- "$tmp/relocated-source")"
[[ "$original_hash" == "$relocated_hash" ]]
printf '<!-- changed dependency -->\n' >> "$tmp/source/pom.xml"
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
