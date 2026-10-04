#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

for obsolete in \
  "$ROOT/deploy/deploy-staging.sh" \
  "$ROOT/deploy/deploy-production.sh" \
  "$ROOT/deploy/deploy-production-remote.sh" \
  "$ROOT/scripts/prepare-release.sh" \
  "$ROOT/scripts/verify-production-release.sh"; do
  [[ ! -e "$obsolete" ]] || {
    printf 'obsolete project release entrypoint still exists: %s\n' "$obsolete" >&2
    exit 1
  }
done

grep -Fq 'AI Agent' "$ROOT/AGENTS.md"
grep -Fq 'release-platform 页面和 Host Agent' "$ROOT/AGENTS.md"
grep -Fq '完整 commit SHA' "$ROOT/docs/engineering/release-platform-only.md"
grep -Fq '不允许从项目工作区直接发布' "$ROOT/deploy/README.md"
if rg -n \
  'deploy/(deploy-staging|deploy-production|deploy-production-remote)\.sh|scripts/(prepare-release|verify-production-release)\.sh' \
  "$ROOT/AGENTS.md" "$ROOT/deploy/README.md" "$ROOT/docs/engineering" "$ROOT/docs/releases" "$ROOT/docs/security" \
  --glob '*.md' > /dev/null; then
  printf 'active project documentation still exposes a project release entrypoint\n' >&2
  exit 1
fi

printf 'release-platform-only contract passed\n'
