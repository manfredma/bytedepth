#!/usr/bin/env bash

# Shared staging runtime facts used by Host Agent integration/E2E runners:
# browser location, runtime manifests, locks, and common readiness helpers.

readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome
readonly SHARED_PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright

staging_runtime_sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

# Release source snapshots have no .git. Follow declared reactor modules rather
# than scanning generated target/node_modules trees, and bind paths as well as bytes.
staging_maven_inputs_sha256() {
  python3 - "$1" << 'PY_MAVEN_INPUTS'
import hashlib
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1]).resolve()
inputs = set()

def add_pom(pom):
    pom = pom.resolve()
    pom.relative_to(root)
    if pom in inputs:
        return
    inputs.add(pom)
    project = ET.parse(pom).getroot()
    modules = project.findall('./{*}modules/{*}module')
    modules += project.findall('./{*}profiles/{*}profile/{*}modules/{*}module')
    for module in modules:
        if module.text is None or not module.text.strip():
            raise ValueError(f'empty reactor module in {pom.relative_to(root)}')
        child = pom.parent / module.text.strip()
        add_pom(child if child.is_file() else child / 'pom.xml')

try:
    add_pom(root / 'pom.xml')
    inputs.update(path for path in (root / '.mvn').rglob('*') if path.is_file())
    inputs.update(root / name for name in ('mvnw', 'mvnw.cmd') if (root / name).is_file())
    digest = hashlib.sha256(b'bytedepth-maven-inputs-v1\0')
    for path in sorted(inputs, key=lambda item: item.relative_to(root).as_posix()):
        relative = path.relative_to(root).as_posix().encode('utf-8')
        content = path.read_bytes()
        for part in (relative, content):
            digest.update(len(part).to_bytes(8, 'big'))
            digest.update(part)
    print(digest.hexdigest())
except (OSError, ET.ParseError, ValueError) as error:
    print(f'Refusing: cannot fingerprint staging Maven dependency inputs: {error}', file=sys.stderr)
    sys.exit(1)
PY_MAVEN_INPUTS
}

staging_ensure_ubuntu_owner() {
  if [[ "${EUID:-}" -eq 0 ]] && getent passwd ubuntu > /dev/null 2>&1 && getent group ubuntu > /dev/null 2>&1; then
    chown ubuntu:ubuntu "$@"
  fi
}

staging_shared_ffmpeg_executable() {
  local executable

  executable="$(find "$SHARED_PLAYWRIGHT_BROWSERS_PATH" -maxdepth 2 -type f \
    -name ffmpeg-linux -perm -u+x -print -quit 2> /dev/null || true)"
  [[ -n "$executable" ]] || return 1
  printf '%s\n' "$executable"
}

staging_shared_ffmpeg_version() {
  local executable

  executable="$(staging_shared_ffmpeg_executable)" || return 1
  "$executable" -version | sed -n '1p'
}

# Deployment must be able to move to a new checkout before bootstrap writes its
# dependency-bound manifest. Keep this preflight limited to immutable shared
# infrastructure; require_staging_runtime verifies the prepared dependencies.
require_staging_runtime_prerequisites() {
  if [[ ! -x "$SHARED_CHROMIUM_EXECUTABLE" ]]; then
    printf 'Refusing: staging Chromium executable is unavailable.\n' >&2
    return 1
  fi
  if ! staging_shared_ffmpeg_executable > /dev/null; then
    printf 'Refusing: shared Playwright ffmpeg is unavailable. Run bootstrap-staging-runtime.sh.\n' >&2
    return 1
  fi
}

write_runtime_manifest() {
  local manifest="$1"
  local source_root="$2"
  local runtime_directory
  local temporary_file
  local lockfile_sha
  local maven_inputs_sha
  local chromium_version
  local playwright_ffmpeg_version

  runtime_directory="$(dirname "$manifest")"
  lockfile_sha="$(staging_runtime_sha256 "$source_root/package-lock.json")"
  maven_inputs_sha="$(staging_maven_inputs_sha256 "$source_root")" || return 1
  if [[ ! -x "$SHARED_CHROMIUM_EXECUTABLE" ]]; then
    printf 'Refusing: staging Chromium executable is unavailable.\n' >&2
    return 1
  fi
  chromium_version="$("$SHARED_CHROMIUM_EXECUTABLE" --version)"
  playwright_ffmpeg_version="$(staging_shared_ffmpeg_version)"

  if [[ "${EUID:-}" -eq 0 ]] && getent passwd ubuntu > /dev/null 2>&1 && getent group ubuntu > /dev/null 2>&1; then
    install -d -o ubuntu -g ubuntu -m 0700 "$runtime_directory"
  else
    install -d -m 0700 "$runtime_directory"
  fi
  temporary_file="$(mktemp "$runtime_directory/.manifest.XXXXXX")"
  staging_ensure_ubuntu_owner "$temporary_file"
  printf 'package_lock_sha256=%s\nmaven_inputs_sha256=%s\nchromium_version=%s\nplaywright_ffmpeg_version=%s\n' \
    "$lockfile_sha" "$maven_inputs_sha" "$chromium_version" "$playwright_ffmpeg_version" > "$temporary_file"
  staging_ensure_ubuntu_owner "$runtime_directory" "$temporary_file"
  chmod 0600 "$temporary_file"
  mv -f "$temporary_file" "$manifest"
}

require_staging_runtime() {
  local manifest="$1"
  local source_root="$2"
  local expected_lock_sha
  local expected_maven_inputs_sha
  local actual_lock_sha
  local actual_maven_inputs_sha
  local actual_chromium_version
  local actual_playwright_ffmpeg_version

  if [[ ! -f "$manifest" || -L "$manifest" ]]; then
    printf 'Refusing: staging runtime manifest is missing. Run bootstrap-staging-runtime.sh.\n' >&2
    return 1
  fi
  actual_lock_sha="$(awk -F= '$1 == "package_lock_sha256" {print $2; exit}' "$manifest")"
  actual_maven_inputs_sha="$(awk -F= '$1 == "maven_inputs_sha256" {print $2; exit}' "$manifest")"
  actual_chromium_version="$(awk -F= '$1 == "chromium_version" {print $2; exit}' "$manifest")"
  actual_playwright_ffmpeg_version="$(awk -F= '$1 == "playwright_ffmpeg_version" {print substr($0, index($0, "=") + 1); exit}' "$manifest")"
  expected_lock_sha="$(staging_runtime_sha256 "$source_root/package-lock.json")"
  expected_maven_inputs_sha="$(staging_maven_inputs_sha256 "$source_root")" || return 1

  if [[ "$actual_lock_sha" != "$expected_lock_sha" || "$actual_maven_inputs_sha" != "$expected_maven_inputs_sha" ]]; then
    printf 'Refusing: staging runtime manifest does not match dependency inputs. Run bootstrap-staging-runtime.sh.\n' >&2
    printf 'Runtime dependency hashes: package_lock recorded=%s expected=%s; maven_inputs recorded=%s expected=%s\n' \
      "$actual_lock_sha" "$expected_lock_sha" "$actual_maven_inputs_sha" "$expected_maven_inputs_sha" >&2
    return 1
  fi
  if [[ ! -x "$SHARED_CHROMIUM_EXECUTABLE" || "$actual_chromium_version" != "$("$SHARED_CHROMIUM_EXECUTABLE" --version)" ]]; then
    printf 'Refusing: manifest Chromium executable changed. Run bootstrap-staging-runtime.sh.\n' >&2
    return 1
  fi
  if [[ -z "$actual_playwright_ffmpeg_version" || "$actual_playwright_ffmpeg_version" != "$(staging_shared_ffmpeg_version)" ]]; then
    printf 'Refusing: manifest Playwright ffmpeg runtime changed. Run bootstrap-staging-runtime.sh.\n' >&2
    return 1
  fi
}

# Called by the candidate runner under the platform deployment/test lock. The
# manifest is written only by successful dependency preparation, never relabeled.
ensure_staging_runtime() {
  local manifest="$1" source_root="$2"
  if require_staging_runtime "$manifest" "$source_root"; then
    return 0
  fi
  printf 'Preparing staging dependencies for candidate source=%s\n' "$source_root"
  RELEASE_PLATFORM_TEST_SOURCE_ROOT="$source_root" \
    RELEASE_PLATFORM_TEST_RUNTIME_MANIFEST="$manifest" \
    bash "$source_root/deploy/bootstrap-staging-runtime.sh" --lock-held --ensure || return $?
  require_staging_runtime "$manifest" "$source_root"
}
