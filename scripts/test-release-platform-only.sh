#!/usr/bin/env bash
set -Eeuo pipefail

# Contract test for the release-platform-only boundary. It protects against
# reintroducing project-side deployment, Tag, SSH, or remote-host entrypoints.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

for obsolete in \
  "$ROOT/deploy/deploy-staging.sh" \
  "$ROOT/deploy/deploy-production.sh" \
  "$ROOT/deploy/deploy-production-remote.sh" \
  "$ROOT/scripts/verify-production-release.sh"; do
  [[ ! -e "$obsolete" ]] || {
    printf 'obsolete project release entrypoint still exists: %s\n' "$obsolete" >&2
    exit 1
  }
done

grep -Fq 'AI Agent' "$ROOT/AGENTS.md"
grep -Fq 'canonical release-platform' "$ROOT/AGENTS.md"
grep -Fq 'Host Agent' "$ROOT/AGENTS.md"
grep -Fq '完整 commit SHA' "$ROOT/docs/engineering/release-platform-only.md"
grep -Fq 'scripts/prepare-release.sh' "$ROOT/docs/engineering/release-platform-only.md"
grep -Fq '不允许从项目工作区直接发布' "$ROOT/deploy/README.md"
grep -Fq 'Agent 使用 canonical API' "$ROOT/docs/releases/README.md"
if rg -n '触发候选任务只允许通过 release-platform 页面|发布.*必须在 release-platform 页面完成' "$ROOT/docs/releases/README.md"; then
  printf 'Agent delivery must follow canonical API documentation.\n' >&2
  exit 1
fi
if rg -n \
  'deploy/(deploy-staging|deploy-production|deploy-production-remote)\.sh|scripts/verify-production-release\.sh' \
  "$ROOT/AGENTS.md" "$ROOT/deploy/README.md" "$ROOT/docs/engineering" "$ROOT/docs/releases" "$ROOT/docs/security" \
  --glob '*.md' > /dev/null; then
  printf 'active project documentation still exposes a project release entrypoint\n' >&2
  exit 1
fi

printf 'release-platform-only contract passed\n'

bash "$ROOT/scripts/test-candidate-source.sh"
