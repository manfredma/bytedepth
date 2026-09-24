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

require_text '宿主机原生' "$DEPLOY_GUIDE"
require_text 'systemd' "$DEPLOY_GUIDE"
require_text 'run-staging-integration-tests.sh' "$DEPLOY_GUIDE"
require_text 'run-staging-e2e-tests.sh' "$DEPLOY_GUIDE"
require_text 'runtime_mode=host-native' "$ROOT/docs/releases/README.md"
require_text 'staging-it' "$ROOT/docs/agent-guides/maven.md"
require_text 'staging-e2e' "$ROOT/docs/agent-guides/maven.md"
require_text '状态不确定' "$ROOT/docs/engineering/gotchas.md"

if rg -n -i 'docker compose|docker-compose|docker restart|docker run|compose up|ctl\.sh|Dockerfile|Testcontainers' \
    "$DEPLOY_GUIDE" "$ROOT/docs/agent-guides/maven.md" "$ROOT/docs/engineering/gotchas.md" >/dev/null; then
    printf 'Normal deployment documentation still instructs the removed Docker runtime.\n' >&2
    exit 1
fi

printf 'Host-native documentation contract passed.\n'
