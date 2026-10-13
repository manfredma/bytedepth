#!/usr/bin/env bash
set -Eeuo pipefail

# Local fixture regression test for handoff documentation checks.
# Called by local quality and the checklist; it does not contact remote systems.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

mkdir -p "$FIXTURE_ROOT/scripts" "$FIXTURE_ROOT/deploy" \
  "$FIXTURE_ROOT/docs/agent-guides" "$FIXTURE_ROOT/docs/releases" \
  "$FIXTURE_ROOT/docs/engineering" "$FIXTURE_ROOT/docs/security"
cp "$SOURCE_ROOT/scripts/test-host-native-docs.sh" "$FIXTURE_ROOT/scripts/"
cp "$SOURCE_ROOT/scripts/test-staging-checklist.sh" "$FIXTURE_ROOT/scripts/"
printf 'release-platform\n' > "$FIXTURE_ROOT/AGENTS.md"
printf 'release-platform systemd\n' > "$FIXTURE_ROOT/deploy/README.md"
printf 'Java 25\n' > "$FIXTURE_ROOT/docs/agent-guides/maven.md"
printf '完整 commit SHA\nscripts/prepare-release.sh\n' > "$FIXTURE_ROOT/docs/releases/README.md"
printf '状态不确定\n' > "$FIXTURE_ROOT/docs/engineering/gotchas.md"
printf 'release-platform\n' > "$FIXTURE_ROOT/docs/engineering/release-platform-only.md"
printf '%s\n' \
  'bash "$SOURCE_ROOT/scripts/test-release-platform-only.sh"' \
  'bash "$SOURCE_ROOT/scripts/test-run-local-quality.sh"' \
  > "$FIXTURE_ROOT/scripts/check-staging-checklist.sh"
chmod +x "$FIXTURE_ROOT/scripts/check-staging-checklist.sh"

expect_status() {
  local expected="$1"
  shift
  local actual=0
  "$@" > "$FIXTURE_ROOT/result.log" 2>&1 || actual=$?
  if [[ "$actual" -ne "$expected" ]]; then
    cat "$FIXTURE_ROOT/result.log" >&2
    printf 'Expected status %s, received %s: %s\n' "$expected" "$actual" "$*" >&2
    exit 1
  fi
}

expect_status 0 bash "$FIXTURE_ROOT/scripts/test-host-native-docs.sh"
expect_status 0 bash "$FIXTURE_ROOT/scripts/test-staging-checklist.sh"

printf '历史主机 124.221.143.25，不是当前环境绑定\n' >> "$FIXTURE_ROOT/AGENTS.md"
expect_status 0 bash "$FIXTURE_ROOT/scripts/test-host-native-docs.sh"

printf 'scripts/prepare-release.sh\n' > "$FIXTURE_ROOT/docs/releases/README.md"
expect_status 1 bash "$FIXTURE_ROOT/scripts/test-host-native-docs.sh"
printf '完整 commit SHA\nscripts/prepare-release.sh\n' > "$FIXTURE_ROOT/docs/releases/README.md"

printf 'deploy/deploy-staging.sh\n' >> "$FIXTURE_ROOT/deploy/README.md"
expect_status 1 bash "$FIXTURE_ROOT/scripts/test-host-native-docs.sh"
expect_status 1 bash "$FIXTURE_ROOT/scripts/test-staging-checklist.sh"

printf 'Handoff documentation fixture regressions passed.\n'
