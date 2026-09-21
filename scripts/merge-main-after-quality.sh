#!/usr/bin/env bash
set -Eeuo pipefail
# Merge a staging-accepted branch to main only after current-SHA GitHub quality passes.
REF="${1:?Usage: $0 <branch>}"
REPO="$(git remote get-url origin | sed -E 's#.*github.com[:/]##; s#\.git$##')"
git fetch origin "$REF" main
SHA="$(git rev-parse "origin/$REF")"
bash scripts/check-staging-changelog-change.sh --target "$SHA" --base origin/main
bash scripts/check-release-readiness.sh --target "$SHA" --base origin/main --mode candidate
command -v gh >/dev/null || { echo 'gh is required' >&2; exit 1; }
for attempt in $(seq 1 120); do
  run="$(gh run list --repo "$REPO" --workflow quality --commit "$SHA" --limit 1 --json status,conclusion --jq '.[0] // {}')"
  status="$(jq -r '.status // "missing"' <<<"$run")"
  conclusion="$(jq -r '.conclusion // ""' <<<"$run")"
  [[ "$status" == completed ]] && break
  [[ "$status" == queued || "$status" == in_progress ]] || { echo "quality workflow missing for $REPO $SHA" >&2; exit 1; }
  sleep 15
done
[[ "$status" == completed && "$conclusion" == success ]] || { echo "quality workflow failed for $REPO $SHA (status=$status conclusion=$conclusion)" >&2; exit 1; }
git merge-base --is-ancestor origin/main "$SHA" || { echo "ref is not fast-forwardable to main" >&2; exit 1; }
git push origin "refs/remotes/origin/$REF:refs/heads/main"
