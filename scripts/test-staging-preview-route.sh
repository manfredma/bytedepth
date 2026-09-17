#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly NGINX_ROOT="$ROOT/deploy/nginx/staging-root.conf"
readonly NGINX_TEMPLATE="$ROOT/deploy/nginx/staging.conf.template"
readonly E2E_RUNNER="$ROOT/deploy/run-staging-e2e-tests.sh"
readonly SYNC_SCRIPT="$ROOT/deploy/sync-prod-to-staging.sh"

[[ -f "$NGINX_ROOT" && -f "$NGINX_TEMPLATE" ]]
grep -Fq 'staging_preview' "$NGINX_ROOT"
grep -Fq 'true 1' "$NGINX_ROOT"
grep -Fq '$arg_preview = false' "$NGINX_TEMPLATE"
grep -Fq 'https://bytedepth.cn' "$NGINX_TEMPLATE"
grep -Fq 'X-Robots-Tag' "$NGINX_TEMPLATE"
grep -Fq 'noindex' "$NGINX_TEMPLATE"
grep -Fq 'Secure' "$NGINX_ROOT"
grep -Fq 'HttpOnly' "$NGINX_ROOT"
grep -Fq 'SameSite=Lax' "$NGINX_ROOT"
grep -Fq 'Max-Age=604800' "$NGINX_ROOT"
grep -Fq 'Max-Age=0' "$NGINX_ROOT"

if awk '
    /listen 80 default_server/ {inside = 1}
    inside && /proxy_pass http:\/\/$app_upstream/ {exit 1}
    inside && /^}/ {inside = 0}
' "$NGINX_TEMPLATE"; then
    :
else
    printf 'Staging HTTP default server must not proxy directly to the app.\n' >&2
    exit 1
fi

grep -Fqx 'readonly E2E_PREVIEW_BOOTSTRAP_URL=https://staging.bytedepth.cn/?preview=true' "$E2E_RUNNER"
grep -Fq 'E2E_PREVIEW_BOOTSTRAP_URL' "$E2E_RUNNER"
grep -Fq 'https://staging.bytedepth.cn/?preview=true' "$SYNC_SCRIPT"

for file in \
    "$ROOT/AGENTS.md" \
    "$ROOT/deploy/README.md" \
    "$ROOT/docs/releases/README.md" \
    "$ROOT/docs/engineering/git-workflow.md" \
    "$ROOT/docs/engineering/unified-release-pipeline.md" \
    "$ROOT/docs/agent-guides/maven.md" \
    "$ROOT/docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md" \
    "$ROOT/docs/superpowers/specs/2026-08-23-staging-environment-design.md" \
    "$ROOT/docs/superpowers/specs/2026-09-08-rss-discovery-and-sync-design.md" \
    "$ROOT/docs/superpowers/plans/2026-09-10-test-boundaries.md" \
    "$ROOT/docs/superpowers/plans/2026-09-10-network-map.md" \
    "$ROOT/docs/superpowers/plans/2026-08-23-staging-environment.md"; do
    grep -Fq '?preview=true' "$file"
done

printf 'Staging preview route contract passed.\n'
