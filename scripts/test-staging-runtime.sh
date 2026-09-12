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

source "$LIBRARY"

readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
readonly SOURCE_ROOT="$TEMP_DIR/source"
readonly STATE_DIR="$TEMP_DIR/state"
mkdir -p "$SOURCE_ROOT/.e2e/chrome-linux64" "$STATE_DIR/runtime"
printf 'lockfile\n' > "$SOURCE_ROOT/package-lock.json"
printf '<project/>\n' > "$SOURCE_ROOT/pom.xml"
printf 'chromium\n' > "$SOURCE_ROOT/.e2e/chrome-linux64/chrome"
chmod +x "$SOURCE_ROOT/.e2e/chrome-linux64/chrome"

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
