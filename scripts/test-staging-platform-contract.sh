#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
for runner in integration e2e; do
  if bash "$root/deploy/run-staging-$runner-tests.sh" --unknown > "$tmp/output" 2>&1; then
    printf 'Unknown argument accepted: %s\n' "$runner" >&2
    exit 1
  fi
  grep -Fq 'Unknown staging test argument' "$tmp/output"
done
source "$root/deploy/lib/staging-platform-contract.sh"
sha=0123456789abcdef0123456789abcdef01234567
digest="sha256:$(printf artifact | shasum -a 256 | awk '{print $1}')"
mkdir -p "$tmp/release/source"
printf '%s\n' "$sha" > "$tmp/release/source/.release-platform-source-commit"
printf 'source archive\n' > "$tmp/release/staging-source.tar.gz"
archive_sha="$(shasum -a 256 "$tmp/release/staging-source.tar.gz" | awk '{print $1}')"
printf 'commit=%s\nsource_commit=%s\nsource_sha256=%s\nartifact_digest=%s\n' "$sha" "$sha" "$archive_sha" "$digest" > "$tmp/release/manifest"
printf 'commit=%s\nartifact_digest=%s\n' "$sha" "$digest" > "$tmp/history"
SOURCE_ROOT="$tmp/release/source"
DEPLOY_HISTORY="$tmp/history"
RELEASE_PLATFORM_TEST_RELEASE_MANIFEST="$tmp/release/manifest"
RELEASE_PLATFORM_TASK_NONCE=attempt-fixture
ARTIFACT_DIGEST="$digest"
if staging_platform_parse_args; then
  echo 'Missing SHA accepted' >&2
  exit 1
fi
if staging_platform_parse_args --commit-sha "$sha" --commit-sha "$sha"; then
  echo 'Duplicate SHA accepted' >&2
  exit 1
fi
staging_platform_parse_args --commit-sha "$sha"
[[ "$(staging_platform_source_commit)" == "$sha" ]]
staging_platform_require_candidate "$sha"
if RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST="sha256:$(printf '%064d' 0)" staging_platform_require_candidate "$sha"; then
  echo 'Conflicting digest accepted' >&2
  exit 1
fi
printf 'tampered\n' >> "$tmp/release/staging-source.tar.gz"
if staging_platform_require_candidate "$sha"; then
  echo 'Tampered archive accepted' >&2
  exit 1
fi
mkdir -p "$tmp/module/target/failsafe-reports"
printf '<failsafe-summary><completed>0</completed><skipped>0</skipped><failures>0</failures><errors>0</errors></failsafe-summary>\n' > "$tmp/module/target/failsafe-reports/failsafe-summary.xml"
if staging_platform_test_count integration "$tmp"; then
  echo 'Zero integration tests accepted' >&2
  exit 1
fi
printf '<failsafe-summary><completed>3</completed><skipped>1</skipped><failures>0</failures><errors>0</errors></failsafe-summary>\n' > "$tmp/module/target/failsafe-reports/failsafe-summary.xml"
[[ "$(staging_platform_test_count integration "$tmp")" == 2 ]]
printf 'staging platform runner contract tests passed\n'
