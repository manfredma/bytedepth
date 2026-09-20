#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CHECKER="$SOURCE_ROOT/scripts/check-changelog-order.sh"
readonly TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

assert_fails() {
    if "$@" >/dev/null 2>&1; then
        printf 'Expected command to fail: %s\n' "$*" >&2
        exit 1
    fi
}

assert_succeeds() {
    if ! "$@" >/dev/null 2>&1; then
        printf 'Expected command to pass: %s\n' "$*" >&2
        exit 1
    fi
}

readonly VALID_CHANGELOG="$TEMP_ROOT/valid.md"
printf '%s\n' \
    '# Changelog' \
    '' \
    '## Unreleased' \
    '' \
    '### Added' \
    '' \
    '- Next capability.' \
    '' \
    '## [v2.18.0] - 2026-09-20' \
    '' \
    '## [v2.17.0] - 2026-09-20' \
    '' \
    '## [v2.15.8] - 2026-09-16' > "$VALID_CHANGELOG"

readonly UNSORTED_CHANGELOG="$TEMP_ROOT/unsorted.md"
printf '%s\n' \
    '# Changelog' \
    '' \
    '## Unreleased' \
    '' \
    '### Added' \
    '' \
    '- Next capability.' \
    '' \
    '## [v2.17.0] - 2026-09-20' \
    '' \
    '## [v2.18.0] - 2026-09-20' > "$UNSORTED_CHANGELOG"

assert_succeeds bash "$CHECKER" "$VALID_CHANGELOG"
assert_fails bash "$CHECKER" "$UNSORTED_CHANGELOG"
assert_succeeds bash "$CHECKER" "$SOURCE_ROOT/docs/releases/CHANGELOG.md"

printf 'Changelog order contract tests passed.\n'
