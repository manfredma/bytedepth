#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly DEPLOY_GUIDE="$ROOT/deploy/README.md"

require_text() {
    local needle="$1"
    local file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing documentation contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

for file in \
    "$ROOT/AGENTS.md" \
    "$DEPLOY_GUIDE" \
    "$ROOT/docs/agent-guides/maven.md" \
    "$ROOT/docs/releases/README.md" \
    "$ROOT/docs/engineering/gotchas.md"; do
    [[ -f "$file" ]] || { printf 'Missing required documentation file: %s\n' "$file" >&2; exit 1; }
done

require_text 'release-platform' "$DEPLOY_GUIDE"
require_text '129.211.6.82' "$ROOT/AGENTS.md"
require_text 'systemd' "$DEPLOY_GUIDE"
require_text '完整 commit SHA' "$ROOT/docs/releases/README.md"
require_text '状态不确定' "$ROOT/docs/engineering/gotchas.md"

if rg -n '124\.221\.143\.25|staging（124|staging \(124\)' \
    "$ROOT/AGENTS.md" "$DEPLOY_GUIDE" "$ROOT/docs/agent-guides/maven.md" \
    "$ROOT/docs/engineering/git-workflow.md" "$ROOT/docs/releases/README.md" \
    "$ROOT/deploy/nginx/staging-edge-certificate.conf" >/dev/null; then
    printf 'Current staging deployment guidance still points to legacy host 124.\n' >&2
    exit 1
fi

if rg -n -i 'docker compose|docker-compose|docker restart|docker run|compose up|deploy-production-remote\.sh|deploy-staging\.sh|deploy-production\.sh' \
    "$DEPLOY_GUIDE" "$ROOT/docs/engineering/release-platform-only.md" "$ROOT/docs/releases/README.md" >/dev/null; then
    printf 'Current deployment documentation still exposes a removed runtime or project release entrypoint.\n' >&2
    exit 1
fi

printf 'Host-native documentation contract passed.\n'
