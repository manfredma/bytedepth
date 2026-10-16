#!/usr/bin/env bash
set -Eeuo pipefail
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT
mkdir -p "$FIXTURE_ROOT/scripts/lib"
cp "$SOURCE_ROOT/scripts/test-run-local-quality.sh" "$FIXTURE_ROOT/scripts/"
cp "$SOURCE_ROOT/scripts/lib/java-25.sh" "$FIXTURE_ROOT/scripts/lib/"
cp "$SOURCE_ROOT/scripts/run-local-quality.sh" "$FIXTURE_ROOT/scripts/"
bash "$FIXTURE_ROOT/scripts/test-run-local-quality.sh" > "$FIXTURE_ROOT/result" 2>&1
for forbidden in \
  'ssh example.invalid true' \
  'bash deploy/run-staging-integration-tests.sh' \
  './mvnw -Pstaging-integration verify' \
  'npm run test:e2e'; do
  cp "$SOURCE_ROOT/scripts/run-local-quality.sh" "$FIXTURE_ROOT/scripts/"
  printf '\n%s\n' "$forbidden" >> "$FIXTURE_ROOT/scripts/run-local-quality.sh"
  if bash "$FIXTURE_ROOT/scripts/test-run-local-quality.sh" > "$FIXTURE_ROOT/result" 2>&1; then
    printf 'Local boundary failed to reject: %s\n' "$forbidden" >&2
    exit 1
  fi
done
printf 'Local quality boundary fixture regressions passed.\n'
