#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$SOURCE_ROOT/scripts/merge-main-after-quality.sh"

grep -Fq 'bash scripts/check-release-readiness.sh --target "$SHA" --base origin/main --mode candidate' "$SCRIPT"
readiness_line="$(rg -nF 'bash scripts/check-release-readiness.sh --target "$SHA" --base origin/main --mode candidate' "$SCRIPT" | cut -d: -f1)"
push_line="$(rg -nF 'git push origin "refs/remotes/origin/$REF:refs/heads/main"' "$SCRIPT" | cut -d: -f1)"
[[ "$readiness_line" -lt "$push_line" ]]

printf 'Merge-main release readiness contract passed.\n'
