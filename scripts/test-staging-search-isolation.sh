#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly NGINX_ROOT="$ROOT/deploy/nginx/staging-root.conf"
readonly NGINX_TEMPLATE="$ROOT/deploy/nginx/staging.conf.template"
readonly STAGING_DEPLOY="$ROOT/deploy/deploy-staging.sh"
readonly NAV_TEMPLATE="$ROOT/bytedepth-start/src/main/resources/templates/fragments/nav.html"
readonly HEAD_TEMPLATE="$ROOT/bytedepth-start/src/main/resources/templates/fragments/pwa-head.html"
readonly PLAYWRIGHT_CONFIG="$ROOT/playwright.config.mjs"
readonly E2E_RUNNER="$ROOT/deploy/run-staging-e2e-tests.sh"

[[ -f "$NGINX_ROOT" && -f "$NGINX_TEMPLATE" ]]
grep -Fq 'location = /robots.txt' "$NGINX_TEMPLATE"
grep -Fq 'User-agent: *\nDisallow: /' "$NGINX_TEMPLATE"
grep -Fq 'location = /feed.xml' "$NGINX_TEMPLATE"
grep -Fq 'location = /sitemap.xml' "$NGINX_TEMPLATE"
grep -Fq 'return 404' "$NGINX_TEMPLATE"
grep -Fq 'X-Robots-Tag "noindex, nofollow, noarchive" always' "$NGINX_TEMPLATE"
grep -Fq 'Referrer-Policy "no-referrer" always' "$NGINX_TEMPLATE"
grep -Fq 'if ($host != ${BYTEDEPTH_DOMAIN})' "$NGINX_TEMPLATE"
grep -Fq 'staging-bytedepth.bytedepth.cn' "$E2E_RUNNER"
grep -Fq 'BYTEDEPTH_ENVIRONMENT=staging' "$STAGING_DEPLOY"
grep -Fq 'BYTEDEPTH_DOMAIN=staging-bytedepth.bytedepth.cn' "$STAGING_DEPLOY"
grep -Fq 'Requires=bytedepth-app.service' "$ROOT/deploy/systemd/nginx.service"
grep -Fq '127.0.0.1:8080' "$ROOT/deploy/nginx/staging.conf.template"
grep -Fq 'th:if="${environment != '\''staging'\''}"' "$NAV_TEMPLATE"
grep -Fq 'th:if="${environment != '\''staging'\''}"' "$HEAD_TEMPLATE"
grep -Fq 'name="robots"' "$HEAD_TEMPLATE"

if rg -n 'staging_preview|E2E_PREVIEW|preview=true|preview=false' \
    "$NGINX_ROOT" "$NGINX_TEMPLATE" "$E2E_RUNNER" "$PLAYWRIGHT_CONFIG"; then
    printf 'Runtime staging routing must not retain query/Cookie preview state.\n' >&2
    exit 1
fi

printf 'Staging search isolation contract passed.\n'
