#!/usr/bin/env bash
set -Eeuo pipefail
# Merge a staging-accepted branch to main only after current-SHA GitHub quality passes.
REF="${1:?Usage: $0 <branch>}"
REPO="$(git remote get-url origin | sed -E 's#.*github.com[:/]##; s#\\.git$##')"
SHA="$(git rev-parse "origin/$REF")"
command -v gh >/dev/null || { echo 'gh is required' >&2; exit 1; }
run="$(gh run list --repo "$REPO" --workflow quality.yml --commit "$SHA" --limit 1 --json status,conclusion --jq '.[0] // {}')"
[[ "$(jq -r '.status // "missing"' <<<"$run")" == completed && "$(jq -r '.conclusion // "missing"' <<<"$run")" == success ]] || {
  echo "quality workflow is not green for $REPO $SHA" >&2; exit 1;
}
git merge-base --is-ancestor "$SHA" origin/main || { echo "ref is not fast-forwardable to main" >&2; exit 1; }
git push origin "refs/remotes/origin/$REF:refs/heads/main"

