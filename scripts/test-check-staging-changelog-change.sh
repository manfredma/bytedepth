#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TEMP_ROOT="$(mktemp -d)"
readonly TEMP_REPO="$TEMP_ROOT/repo"
trap 'rm -rf "$TEMP_ROOT"' EXIT

mkdir -p "$TEMP_REPO/docs/releases" "$TEMP_REPO/src/main/java"
git -C "$TEMP_REPO" init -q -b main
git -C "$TEMP_REPO" config user.email test@example.com
git -C "$TEMP_REPO" config user.name staging-changelog-test
printf '%s\n' '# Changelog' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
printf '%s\n' 'class Existing {}' > "$TEMP_REPO/src/main/java/Existing.java"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm base

assert_fails() {
    if "$@" >/dev/null 2>&1; then
        printf 'Expected command to fail: %s\n' "$*" >&2
        exit 1
    fi
}

git -C "$TEMP_REPO" checkout -q -b candidate
printf '%s\n' 'class RuntimeChange {}' > "$TEMP_REPO/src/main/java/RuntimeChange.java"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm runtime-only
run_check() {
    (cd "$TEMP_REPO" && bash "$SOURCE_ROOT/scripts/check-staging-changelog-change.sh" "$@")
}

assert_fails run_check --target candidate --base main

printf '%s\n' '# Changelog' '' '## Unreleased' '' '### Changed' '' '- Candidate is frozen for staging.' \
    > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm freeze-changelog
run_check --target candidate --base main

assert_fails run_check --target main --base main

printf 'Staging Changelog gate contract tests passed.\n'
