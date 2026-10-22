#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly FORMATTER="$SOURCE_ROOT/scripts/format-code.sh"
readonly FORMAT_CHECKER="$SOURCE_ROOT/scripts/format-check.sh"
readonly FIXTURE_ROOT="$SOURCE_ROOT/scripts/test-fixtures/format"
readonly TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

assert_contains() {
  local expected="$1"
  local file="$2"
  grep -Fq "$expected" "$file"
}

[[ -x "$FORMATTER" ]]
[[ -x "$FORMAT_CHECKER" ]]
[[ -f "$SOURCE_ROOT/.editorconfig" ]]
[[ -f "$SOURCE_ROOT/.prettierrc.json" ]]
[[ -f "$SOURCE_ROOT/.prettierignore" ]]
grep -Fq 'spotless-maven-plugin' "$SOURCE_ROOT/pom.xml"
grep -Fq 'palantirJavaFormat' "$SOURCE_ROOT/pom.xml"
grep -Fq 'palantir-java-format.version' "$SOURCE_ROOT/pom.xml"
! bash "$FORMATTER" > /dev/null 2>&1
! rg -n 'git[[:space:]]+(add|commit|reset|checkout)' "$FORMATTER" "$FORMAT_CHECKER" "$SOURCE_ROOT/scripts/lib/format-common.sh"

cp "$FIXTURE_ROOT/malformed.json" "$TEMP_ROOT/sample.json"
cp "$FIXTURE_ROOT/malformed.sh" "$TEMP_ROOT/sample.sh"
cp "$FIXTURE_ROOT/malformed.md" "$TEMP_ROOT/sample.md"
chmod 755 "$TEMP_ROOT/sample.sh"

bash "$FORMATTER" "$TEMP_ROOT/sample.json" "$TEMP_ROOT/sample.sh" "$TEMP_ROOT/sample.md"

assert_contains '"name": "ByteDepth"' "$TEMP_ROOT/sample.json"
assert_contains '"enabled": true' "$TEMP_ROOT/sample.json"
assert_contains '# Formatting fixture' "$TEMP_ROOT/sample.md"
assert_contains 'if [ "$enabled" = true ]; then' "$TEMP_ROOT/sample.sh"
[[ "$(stat -f '%Lp' "$TEMP_ROOT/sample.sh")" == '755' ]]

bash "$FORMAT_CHECKER" "$TEMP_ROOT/sample.json" "$TEMP_ROOT/sample.sh" "$TEMP_ROOT/sample.md"
cp "$TEMP_ROOT/sample.json" "$TEMP_ROOT/sample.json.formatted"
bash "$FORMATTER" "$TEMP_ROOT/sample.json" "$TEMP_ROOT/sample.sh" "$TEMP_ROOT/sample.md"
cmp -s "$TEMP_ROOT/sample.json" "$TEMP_ROOT/sample.json.formatted"

# Generated Expo config remains after a successful quality run; the next run must
# collect the same source files instead of parsing generated/native output.
bash -s "$SOURCE_ROOT/scripts/lib/format-common.sh" "$TEMP_ROOT/generated-scope" << 'GENERATED_SCOPE'
  set -Eeuo pipefail
  source "$1"
  mkdir -p "$2"
  SOURCE_ROOT="$(cd "$2" && pwd -P)"
  mkdir -p "$SOURCE_ROOT/apps/mobile/"{.expo,android,ios,src,plugins}
  for generated in .expo/config.json android/generated.json ios/generated.json; do
    printf '{ intentionally invalid generated JSON' > "$SOURCE_ROOT/apps/mobile/$generated"
  done
  printf 'const value = 1;\n' > "$SOURCE_ROOT/apps/mobile/src/example.ts"
  printf 'export default {};\n' > "$SOURCE_ROOT/apps/mobile/plugins/example.js"
  for scope in all paths; do
    format_collect_scope "$scope" "$SOURCE_ROOT/apps/mobile"
    [[ "${#FORMAT_FILES[@]}" == 2 ]] || {
      printf 'Expected 2 source files, collected %s (generated outputs leaked).\n' "${#FORMAT_FILES[@]}" >&2
      exit 1
    }
    [[ "${FORMAT_FILES[0]}" == "$SOURCE_ROOT/apps/mobile/plugins/example.js" ]]
    [[ "${FORMAT_FILES[1]}" == "$SOURCE_ROOT/apps/mobile/src/example.ts" ]]
  done
  format_collect_scope paths "$SOURCE_ROOT/apps/mobile/.expo/config.json" "$SOURCE_ROOT/apps/mobile/android/generated.json" "$SOURCE_ROOT/apps/mobile/ios/generated.json"
  [[ "${#FORMAT_FILES[@]}" == 0 ]]
GENERATED_SCOPE

printf 'Formatting contract passed.\n'
