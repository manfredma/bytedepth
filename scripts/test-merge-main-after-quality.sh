#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly SCRIPT="$SOURCE_ROOT/scripts/merge-main-after-quality.sh"

grep -Fq "git fetch origin \"refs/heads/\$REF:refs/remotes/origin/\$REF\" \"refs/heads/main:refs/remotes/origin/main\"" "$SCRIPT"
grep -Fq "bash scripts/check-staging-changelog-change.sh --target \"\$SHA\" --base origin/main" "$SCRIPT"
grep -Fq "bash scripts/check-release-readiness.sh --target \"\$SHA\" --base origin/main --mode frozen-candidate" "$SCRIPT"
changelog_gate_line="$(rg -nF 'bash scripts/check-staging-changelog-change.sh --target "$SHA" --base origin/main' "$SCRIPT" | cut -d: -f1)"
readiness_line="$(rg -nF 'bash scripts/check-release-readiness.sh --target "$SHA" --base origin/main --mode frozen-candidate' "$SCRIPT" | cut -d: -f1)"
push_line="$(rg -nF 'git push origin "refs/remotes/origin/$REF:refs/heads/main"' "$SCRIPT" | cut -d: -f1)"
[[ "$readiness_line" -lt "$push_line" ]]
[[ "$changelog_gate_line" -lt "$readiness_line" ]]

printf 'Merge-main release readiness contract passed.\n'
