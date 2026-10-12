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
printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.25.15] - 2026-09-26' '' \
  '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Previous release.' \
  > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm base
git -C "$TEMP_REPO" branch base
git -C "$TEMP_REPO" tag -a -m 'v2.25.15 deployed release' v2.25.15 base

run_check() {
  (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base base)
}

run_frozen_check() {
  (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base base --mode frozen-candidate)
}

run_same_commit_release_check() {
  (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base HEAD --mode release --expected-release 2.26.0)
}

run_frozen_check_against() {
  local base_ref="$1"
  (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base "$base_ref" --mode frozen-candidate)
}

run_release_check_against() {
  local base_ref="$1"
  (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base "$base_ref" --mode release --expected-release 2.26.1)
}

run_main_release_check() {
  (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base v2.26.0)
}

assert_fails() {
  if "$@" > /dev/null 2>&1; then
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
assert_fails run_frozen_check

printf '%s\n' '## Unreleased' '' '### Added' '' '- New capability.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm valid-changelog
run_check

printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.26.0] - 2026-09-26' '' \
  '**Tag**：`v2.26.0`' '**回滚基线**：`v2.25.15`' '' '### Fixed' '' \
  '- Candidate change.' '' '## [v2.25.15] - 2026-09-26' '' \
  '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Previous release.' \
  > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm frozen-changelog
run_frozen_check
run_same_commit_release_check
git -C "$TEMP_REPO" tag -a -m 'v2.26.0 release' v2.26.0 HEAD

# A tag can exist without ever reaching production.  A later candidate must
# keep the last deployed rollback tag, even though its version is below the
# newest tagged candidate.
git -C "$TEMP_REPO" checkout -q -b next-patch v2.26.0
printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.26.1] - 2026-09-26' '' \
  '**Tag**：`v2.26.1`' '**回滚基线**：`v2.25.15`' '' '### Fixed' '' \
  '- Native rollback update.' '' '## [v2.26.0] - 2026-09-26' '' \
  '**Tag**：`v2.26.0`' '**回滚基线**：`v2.25.15`' '' '### Fixed' '' \
  '- Candidate change.' '' '## [v2.25.15] - 2026-09-26' '' \
  '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Previous release.' \
  > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm next-patch
run_frozen_check_against v2.26.0
run_release_check_against v2.26.0

printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.25.15] - 2026-09-26' '' \
  '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Reused old release.' '' \
  '## [v2.25.14] - 2026-09-26' '' '**Tag**：`v2.25.14`' '**回滚基线**：`v2.25.2`' '' \
  '### Fixed' '' '- Previous release.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm reused-release-version
assert_fails run_frozen_check
assert_fails run_same_commit_release_check

printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.26.0] - 2026-09-26' '' \
  '**Tag**：`v2.25.14`' '**回滚基线**：`v2.25.15`' '' '### Fixed' '' '- Candidate change.' '' \
  '## [v2.25.15] - 2026-09-26' '' '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' \
  '### Fixed' '' '- Previous release.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm mismatched-tag
assert_fails run_frozen_check

printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.26.0] - 2026-09-26' '' \
  '**Tag**：`v2.26.0`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Candidate change.' '' \
  '## [v2.25.15] - 2026-09-26' '' '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' \
  '### Fixed' '' '- Previous release.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm mismatched-rollback
assert_fails run_frozen_check

printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.26.0] - 2026-09-26' '' \
  '**Tag**：`v2.26.0`' '**回滚基线**：`v2.25.15`' '' '- Unclassified release note.' '' \
  '### Fixed' '' '## [v2.25.15] - 2026-09-26' '' '**Tag**：`v2.25.15`' \
  '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Previous release.' \
  > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm uncategorized-release-note
assert_fails run_frozen_check

printf '%s\n' '# Changelog' '' '## Unreleased' '' 'No pending changes.' '' \
  '## [v2.26.0] - 2026-09-26' '' '**Tag**：`v2.26.0`' '**回滚基线**：`v2.25.15`' '' \
  '### Fixed' '' '- Candidate change.' '' '## [v2.25.15] - 2026-09-26' '' \
  '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Previous release.' \
  > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm prose-in-unreleased
assert_fails run_frozen_check

printf '%s\n' '# Changelog' '' '## Unreleased' '' '### Fixed' '' '- Stale shipped change.' '' \
  '## [v2.26.0] - 2026-09-26' '' '**Tag**：`v2.26.0`' '**回滚基线**：`v2.25.15`' '' \
  '### Fixed' '' '- Candidate change.' '' '## [v2.25.15] - 2026-09-26' '' \
  '**Tag**：`v2.25.15`' '**回滚基线**：`v2.25.2`' '' '### Fixed' '' '- Previous release.' \
  > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm stale-frozen-changelog
assert_fails run_frozen_check
assert_fails run_same_commit_release_check

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

# A quiet post-release main quality run must accept the frozen release that
# is identical to its base; it must still validate the release metadata.
git -C "$TEMP_REPO" checkout -q -b main-release-state v2.26.0
run_main_release_check

if (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base missing-base) > /dev/null 2>&1; then
  printf 'Expected missing base ref to fail.\n' >&2
  exit 1
fi

# Platform-managed releases freeze notes with prepare-release without legacy
# Tag/rollback fields. Both the released base and next PR head must be accepted.
git -C "$TEMP_REPO" checkout -q -b platform-base base
printf '%s\n' '# Changelog' '' '## Unreleased' '' '## [v2.28.0] - 2026-10-10' '' \
  '### Fixed' '' '- Platform-managed previous release.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm platform-base
git -C "$TEMP_REPO" tag -a -m 'v2.28.0 release' v2.28.0 HEAD
git -C "$TEMP_REPO" checkout -q -b platform-candidate
mkdir -p "$TEMP_REPO/src/main/java"
printf 'class PlatformChange {}\n' > "$TEMP_REPO/src/main/java/PlatformChange.java"
sed -i.bak '/^## Unreleased$/a\
\
### Fixed\
\
- Platform-managed candidate change.\
' "$TEMP_REPO/docs/releases/CHANGELOG.md"
rm "$TEMP_REPO/docs/releases/CHANGELOG.md.bak"
(cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --base platform-base)
bash "$SOURCE_ROOT/scripts/prepare-release.sh" --patch --date 2026-10-12 \
  --changelog "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm platform-frozen-candidate
run_frozen_check_against platform-base
(cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --base platform-base --mode release --expected-release 2.28.1)
(cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --base HEAD)

# A supplied valid rollback declaration still accepts its annotated base tag.
awk '
  { print }
  /^## \[v2.28.1\] - / { print "**回滚基线**：`v2.28.0`" }
' "$TEMP_REPO/docs/releases/CHANGELOG.md" > "$TEMP_ROOT/valid-rollback.md"
cp "$TEMP_ROOT/valid-rollback.md" "$TEMP_REPO/docs/releases/CHANGELOG.md"
run_frozen_check_against platform-base
git -C "$TEMP_REPO" checkout -- docs/releases/CHANGELOG.md

# Optional legacy metadata must still reject conflicting or malformed values.
git -C "$TEMP_REPO" tag v2.27.9 platform-base
for invalid_field in '**Tag**：`v2.28.0`' '**Tag**：invalid' '**回滚基线**：invalid' \
  '**回滚基线**：`v2.28.1`' '**回滚基线**：`v2.25.2`' '**回滚基线**：`v2.27.9`'; do
  git -C "$TEMP_REPO" checkout -- docs/releases/CHANGELOG.md
  awk -v field="$invalid_field" '
    { print }
    /^## \[v2.28.1\] - / { print field }
  ' "$TEMP_REPO/docs/releases/CHANGELOG.md" > "$TEMP_ROOT/invalid-changelog.md"
  cp "$TEMP_ROOT/invalid-changelog.md" "$TEMP_REPO/docs/releases/CHANGELOG.md"
  assert_fails run_frozen_check_against platform-base
done
git -C "$TEMP_REPO" checkout -- docs/releases/CHANGELOG.md

# Absence on the base is valid; an explicitly wrong base Tag remains invalid.
git -C "$TEMP_REPO" checkout -q -b platform-invalid-base platform-base
sed -i.bak '/^## \[v2.28.0\] - /a\
**Tag**：`v2.27.0`\
' "$TEMP_REPO/docs/releases/CHANGELOG.md"
rm "$TEMP_REPO/docs/releases/CHANGELOG.md.bak"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm platform-invalid-base
git -C "$TEMP_REPO" checkout -q platform-candidate
assert_fails run_frozen_check_against platform-invalid-base

printf 'Release readiness checker contract tests passed.\n'
