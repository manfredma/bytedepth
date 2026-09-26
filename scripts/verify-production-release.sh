#!/usr/bin/env bash
set -Eeuo pipefail

# Read-only production acceptance gate. Run on the production host only after
# deploy/deploy-production.sh has deployed the requested annotated SemVer tag.
if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./scripts/verify-production-release.sh vX.Y.Z\n' >&2
    exit 1
fi

readonly HISTORY_FILE=/var/lib/bytedepth-deploy/release-history
readonly BASE_URL=https://bytedepth.cn
readonly CURRENT_MANIFEST=/opt/bytedepth/production-green/current/artifact.manifest
readonly GREEN_APP_SERVICE=bytedepth-production-green-app.service
readonly GREEN_EDGE_SERVICE=bytedepth-production-green-edge.service
readonly GREEN_MYSQL_SERVICE=bytedepth-production-green-mysql.service
readonly GREEN_REDIS_SERVICE=bytedepth-production-green-redis.service
readonly GREEN_MEILI_SERVICE=bytedepth-production-green-meilisearch.service
readonly GREEN_PUBLIC_NGINX_SERVICE=bytedepth-production-green-public-nginx.service
readonly GREEN_PUBLIC_NGINX_CONFIG=/etc/bytedepth/production-green-public-nginx.conf
readonly DOCKER_APP=bytedepth-bytedepth-app-1
readonly DOCKER_NGINX=bytedepth-nginx-1
readonly TAG="${1:-}"
readonly CURL_OPTIONS=(
    --fail
    --silent
    --show-error
    --retry 12
    --retry-delay 5
    --retry-connrefused
    --retry-max-time 120
    --connect-timeout 10
)

[[ "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
    printf 'Usage: sudo ./scripts/verify-production-release.sh vX.Y.Z\n' >&2
    exit 1
}

expected_commit="$(awk -F= -v tag="$TAG" '
    $1 == "version" { current = $2 }
    $1 == "commit" && current == tag { value = $2 }
    END { print value }
' "$HISTORY_FILE" 2>/dev/null || true)"
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] || {
    printf 'Refusing: %s has no recorded production deployment.\n' "$TAG" >&2
    exit 1
}
actual_commit="$(awk -F= '$1 == "commit" {print $2; exit}' "$CURRENT_MANIFEST" 2>/dev/null || true)"
[[ "$actual_commit" == "$expected_commit" ]] || {
    printf 'Refusing: current artifact does not match recorded deployment for %s.\n' "$TAG" >&2
    exit 1
}
for service in "$GREEN_APP_SERVICE" "$GREEN_EDGE_SERVICE" "$GREEN_MYSQL_SERVICE" "$GREEN_REDIS_SERVICE" "$GREEN_MEILI_SERVICE" "$GREEN_PUBLIC_NGINX_SERVICE"; do
    systemctl is-active --quiet "$service" || {
        printf 'Refusing: production green service is not active: %s.\n' "$service" >&2
        exit 1
    }
done
[[ "$(docker inspect -f '{{.State.Running}}' "$DOCKER_APP")" == false ]] || {
    printf 'Refusing: Docker blue application must remain stopped after native cutover.\n' >&2
    exit 1
}
[[ "$(docker inspect -f '{{.State.Running}}' "$DOCKER_NGINX")" == false ]] || {
    printf 'Refusing: Docker blue Nginx must remain stopped after native cutover.\n' >&2
    exit 1
}
grep -Fq 'proxy_pass http://127.0.0.1:18081;' "$GREEN_PUBLIC_NGINX_CONFIG" || {
    printf 'Refusing: native green public Nginx is not routed to the green edge.\n' >&2
    exit 1
}
version_response="$(curl --fail --silent --show-error --retry 12 --retry-delay 5 --retry-connrefused \
    --connect-timeout 10 "$BASE_URL/version")"
jq --exit-status --arg expected_commit "$expected_commit" --arg expected_version "${TAG#v}" \
    '.commitId == $expected_commit and .version == $expected_version' <<< "$version_response" >/dev/null || {
    printf 'Refusing: production /version does not match %s (%s).\n' "$TAG" "$expected_commit" >&2
    exit 1
}

request() {
    curl "${CURL_OPTIONS[@]}" "$BASE_URL$1" >/dev/null
}

fetch_page() {
    curl "${CURL_OPTIONS[@]}" "$BASE_URL$1"
}

# Verify stable public read paths. Discover content-specific paths from the
# live lists, so this check remains valid as editorial content changes.
request /
posts_html="$(fetch_page /posts)"
columns_html="$(fetch_page /columns)"
request /search
request /projects
post_path="$(sed -n 's/.*href="\(\/posts\/[a-z0-9-]*\)".*/\1/p' <<< "$posts_html" | head -n 1)"
column_path="$(sed -n 's/.*href="\(\/columns\/[a-z0-9-]*\)".*/\1/p' <<< "$columns_html" | head -n 1)"
[[ -n "$post_path" && -n "$column_path" ]] || {
    printf 'Refusing: production lists contain no readable post or column path.\n' >&2
    exit 1
}
request "$post_path"
request "$column_path"

# Application logs are checked through the fixed native systemd service.
log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT
journalctl -u "$GREEN_APP_SERVICE" -n 300 --no-pager > "$log_file" 2>&1
if grep -Eqi '\bWARN(ING)?\b|\bERROR\b' "$log_file"; then
    printf 'Refusing: production application logs contain WARNING or ERROR.\n' >&2
    exit 1
fi

printf 'Production verification passed for %s (%s).\n' "$TAG" "$expected_commit"
