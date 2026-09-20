#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TEMP_ROOT="$(mktemp -d)"
readonly TEMP_REPO="$TEMP_ROOT/repo"
trap 'rm -rf "$TEMP_ROOT"' EXIT

mkdir -p "$TEMP_REPO/docs/releases" "$TEMP_REPO/scripts"
cp "$SOURCE_ROOT/scripts/check-changelog-order.sh" "$TEMP_REPO/scripts/check-changelog-order.sh"
git -C "$TEMP_REPO" init -q -b main
git -C "$TEMP_REPO" config user.email test@example.com
git -C "$TEMP_REPO" config user.name readiness-test
printf '%s\n' '# Changelog' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm base
git -C "$TEMP_REPO" branch base

run_check() {
    (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base base)
}

assert_fails() {
    if "$@" >/dev/null 2>&1; then
        printf 'Expected command to fail: %s\n' "$*" >&2
        exit 1
    fi
}

git -C "$TEMP_REPO" checkout -q -b candidate base
mkdir -p "$TEMP_REPO/src/main/java"
printf 'class Change {}\n' > "$TEMP_REPO/src/main/java/Change.java"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm runtime-change
assert_fails run_check

printf '%s\n' '## Unreleased' '' '### Added' '' '- New capability.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm valid-changelog
run_check

for invalid_content in \
    $'## Unreleased\n' \
    $'## Unreleased\n\n### Added\n' \
    $'## Unreleased\n\n### Added\n\n<!-- explain later -->\n'; do
    printf '%s' "$invalid_content" > "$TEMP_REPO/docs/releases/CHANGELOG.md"
    git -C "$TEMP_REPO" add .
    git -C "$TEMP_REPO" commit -qm invalid-changelog
    assert_fails run_check
done

git -C "$TEMP_REPO" checkout -q -b docs-only base
mkdir -p "$TEMP_REPO/docs/engineering"
printf 'Process note.\n' > "$TEMP_REPO/docs/engineering/process.md"
printf '%s\n' '## Unreleased' '' '### Added' '' '- Existing capability.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm docs-only
run_check

git -C "$TEMP_REPO" checkout -q -b deployment-only base
printf 'service: staging\n' > "$TEMP_REPO/deploy.yml"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm deployment-change
assert_fails run_check

if (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base missing-base) >/dev/null 2>&1; then
    printf 'Expected missing base ref to fail.\n' >&2
    exit 1
fi

printf 'Release readiness checker contract tests passed.\n'
