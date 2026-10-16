#!/usr/bin/env bash
set -Eeuo pipefail
# Read-only preflight: bind handoff metadata to both this checkout and the PR.
if [[ $# != 2 || ! "$1" =~ ^[1-9][0-9]*$ || ! "$2" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'Usage: bash scripts/check-candidate-source.sh PR_NUMBER FULL_SHA\n' >&2
  exit 2
fi
readonly PR_NUMBER="$1"
readonly CANDIDATE_SHA="$2"
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SOURCE_ROOT"
local_sha="$(git rev-parse HEAD)"
pr_sha="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
if [[ "$CANDIDATE_SHA" != "$local_sha" || "$CANDIDATE_SHA" != "$pr_sha" ]]; then
  printf 'Candidate source refused: supplied SHA must equal local HEAD and PR head.\n' >&2
  exit 1
fi
printf 'Candidate source verified: refs/pull/%s/head %s\n' "$PR_NUMBER" "$CANDIDATE_SHA"
