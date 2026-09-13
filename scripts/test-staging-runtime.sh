#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly LIBRARY="$ROOT/deploy/lib/staging-runtime.sh"
readonly BOOTSTRAP="$ROOT/deploy/bootstrap-staging-runtime.sh"

[[ -x "$BOOTSTRAP" ]] || {
    printf 'Expected executable staging runtime bootstrap.\n' >&2
    exit 1
}
rg -F 'mvn clean install -DskipTests -Dsort.skip=true' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must prewarm the complete Maven reactor.\n' >&2
    exit 1
}
if rg -q 'playwright install' "$BOOTSTRAP"; then
    printf 'Bootstrap must reuse the staged Chromium instead of downloading a browser.\n' >&2
    exit 1
fi
rg -F '"${1:-}" != --lock-held' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must support an inherited staging lock.\n' >&2
    exit 1
}
rg -F 'exec flock -x "$LOCK_FILE" "$0" --lock-held' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must re-exec under its own staging lock when not inherited.\n' >&2
    exit 1
}

readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
readonly SOURCE_ROOT="$TEMP_DIR/source"
readonly STATE_DIR="$TEMP_DIR/state"
readonly FIXTURE_CHROMIUM="$TEMP_DIR/shared-e2e/chrome-linux64/chrome"
readonly RUNTIME_LIBRARY="$TEMP_DIR/staging-runtime.sh"
mkdir -p "$SOURCE_ROOT" "$(dirname "$FIXTURE_CHROMIUM")" "$STATE_DIR/runtime"
printf 'lockfile\n' > "$SOURCE_ROOT/package-lock.json"
printf '<project/>\n' > "$SOURCE_ROOT/pom.xml"
cat > "$FIXTURE_CHROMIUM" <<'SCRIPT'
#!/usr/bin/env bash
printf 'Google Chrome for Testing 151.0.7922.34\n'
SCRIPT
chmod +x "$FIXTURE_CHROMIUM"

grep -Fqx 'readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome' "$LIBRARY"
if rg -q '\.e2e/chrome-linux64|chromium_path|chromium_sha' "$LIBRARY"; then
    printf 'Staging runtime must not retain a project-local Chromium contract.\n' >&2
    exit 1
fi
sed 's@^readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome$@readonly SHARED_CHROMIUM_EXECUTABLE='"$FIXTURE_CHROMIUM"'@' \
    "$LIBRARY" > "$RUNTIME_LIBRARY"
source "$RUNTIME_LIBRARY"

readonly MANIFEST="$STATE_DIR/runtime/manifest"
write_runtime_manifest "$MANIFEST" "$SOURCE_ROOT" 0123456789abcdef0123456789abcdef01234567
require_staging_runtime "$MANIFEST" "$SOURCE_ROOT" 0123456789abcdef0123456789abcdef01234567
require_staging_runtime "$MANIFEST" "$SOURCE_ROOT" fedcba9876543210fedcba9876543210fedcba98

printf 'changed lockfile\n' >> "$SOURCE_ROOT/package-lock.json"
if require_staging_runtime "$MANIFEST" "$SOURCE_ROOT" 0123456789abcdef0123456789abcdef01234567; then
    printf 'Expected lockfile mismatch to be rejected.\n' >&2
    exit 1
fi

printf 'Staging runtime contract passed.\n'
