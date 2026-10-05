#!/usr/bin/env bash
set -Eeuo pipefail

# Static contract for the repository-side staging boundary. The platform owns
# staging execution; this test only checks that active docs and checklist rules
# do not expose removed project deployment entrypoints.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHECKLIST="$ROOT/scripts/check-staging-checklist.sh"

[[ -x "$CHECKLIST" ]]
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-release-platform-only.sh"' "$CHECKLIST"
grep -Fqx 'bash "$SOURCE_ROOT/scripts/test-run-local-quality.sh"' "$CHECKLIST"
grep -Fq 'release-platform' "$ROOT/AGENTS.md"
grep -Fq '完整 commit SHA' "$ROOT/docs/releases/README.md"
if rg -n 'deploy/(deploy-staging|deploy-production|deploy-production-remote)\.sh|scripts/(prepare-release|verify-production-release)\.sh' \
  "$ROOT/AGENTS.md" "$ROOT/deploy" "$ROOT/docs/security" "$ROOT/docs/engineering" "$ROOT/docs/releases" \
  --glob '*.md' > /dev/null; then
  printf 'The active staging checklist still exposes a project release entrypoint.\n' >&2
  exit 1
fi

printf 'Staging checklist contract passed.\n'
