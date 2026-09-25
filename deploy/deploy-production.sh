#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "${EUID}" -ne 0 ]]; then
    printf 'This is a production-host-only script. From the local checkout run:\n' >&2
    printf "BYTEDEPTH_PRODUCTION_SSH_KEY=\"\$HOME/.ssh/ubuntu_2.pem\" ./deploy/deploy-production-remote.sh vX.Y.Z\n" >&2
    exit 1
fi
if [[ $# -ne 5 || "${1:-}" != --artifact || "${3:-}" != --manifest ]]; then
    printf 'Usage: sudo ./deploy/deploy-production.sh --artifact JAR --manifest MANIFEST vX.Y.Z\n' >&2
    exit 2
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly STATE_DIR=/var/lib/bytedepth-deploy
readonly HISTORY_FILE="$STATE_DIR/release-history"
readonly GREEN_STATE_DIR=/var/lib/bytedepth-production-green
readonly TAG="$5"
readonly JAR="$2"
readonly MANIFEST="$4"
readonly DEPLOY_LOCK=/var/lock/bytedepth-production-deploy.lock
readonly DOCKER_APP=bytedepth-bytedepth-app-1
readonly DOCKER_NGINX=bytedepth-nginx-1
readonly NGINX_CONFIG="$SOURCE_ROOT/deploy/nginx/nginx.conf"
readonly NGINX_BACKUP="$GREEN_STATE_DIR/blue-nginx.conf.before-$TAG"
readonly BLUE_UPSTREAM='127.0.0.1:8080'
readonly GREEN_UPSTREAM='172.18.0.1:18081'
readonly PUBLIC_BASE_URL=https://bytedepth.cn

exec 9>"$DEPLOY_LOCK"
flock -n 9 || { printf 'Refusing deployment: another production deployment is running.\n' >&2; exit 1; }

# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/artifact.sh"
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/production-green-target.sh"
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/production-green-migration.sh"

validate_release_tag "$TAG" || { printf 'Release tag must use stable SemVer.\n' >&2; exit 1; }
[[ -f "$JAR" && -f "$MANIFEST" ]] || { printf 'Uploaded artifact or manifest is missing.\n' >&2; exit 1; }
validate_artifact_manifest "$MANIFEST" "$JAR" || { printf 'Refusing deployment: invalid artifact manifest.\n' >&2; exit 1; }
[[ "$(artifact_manifest_value release_ref "$MANIFEST")" == "$TAG" ]] || {
    printf 'Refusing deployment: manifest tag does not match requested tag.\n' >&2
    exit 1
}

git_cmd() { git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" "$@"; }
[[ "$(git_cmd cat-file -t "refs/tags/$TAG" 2>/dev/null || true)" == tag ]] || {
    printf 'Refusing deployment: %s must be an annotated tag on this host.\n' "$TAG" >&2
    exit 1
}
commit="$(artifact_manifest_value commit "$MANIFEST")"
[[ "$(git_cmd rev-parse "$TAG^{commit}")" == "$commit" ]] || {
    printf 'Refusing deployment: artifact commit does not match annotated tag.\n' >&2
    exit 1
}

install -d -o ubuntu -g ubuntu -m 0700 "$STATE_DIR" "$GREEN_STATE_DIR"
touch "$HISTORY_FILE"
chmod 0600 "$HISTORY_FILE"
chown ubuntu:ubuntu "$HISTORY_FILE"
grep -Fqx "version=$TAG" "$HISTORY_FILE" && {
    printf 'Refusing: %s was already deployed on this node.\n' "$TAG" >&2
    exit 1
}

config_changed=0
blue_stopped=0
route_changed=0
deployment_succeeded=0
rollback_required=0
green_prepare_started=0

current_deploy_mode() {
    awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value=$2} END {print value}' "$CONFIG_FILE" 2>/dev/null || true
}

ensure_production_mode() {
    local current="$1" temp
    [[ "$current" == data-access || "$current" == production ]] || {
        printf 'Refusing: unexpected BYTEDEPTH_DEPLOY_MODE on production host.\n' >&2
        return 1
    }
    if [[ "$current" != production ]]; then
        temp="$(mktemp /etc/.bytedepth-deploy.conf.XXXXXX)"
        printf '%s\n' 'BYTEDEPTH_DEPLOY_MODE=production' > "$temp"
        install -o ubuntu -g ubuntu -m 0600 "$temp" "$CONFIG_FILE"
        rm -f -- "$temp"
        config_changed=1
    fi
}

prepare_green_config() {
    install -d -o ubuntu -g ubuntu -m 0700 /etc/bytedepth
    if [[ ! -f /etc/bytedepth/production-green.conf ]]; then
        install -o ubuntu -g ubuntu -m 0600 \
            "$SOURCE_ROOT/deploy/production-green.conf.example" \
            /etc/bytedepth/production-green.conf
    fi
    load_production_green_target
    export BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT="$GREEN_STATE_DIR"
}

require_blue_route() {
    [[ -f "$NGINX_CONFIG" && ! -L "$NGINX_CONFIG" ]] || {
        printf 'Refusing: Docker Nginx configuration is missing.\n' >&2
        return 1
    }
    grep -Fq "proxy_pass http://$BLUE_UPSTREAM;" "$NGINX_CONFIG" || {
        printf 'Refusing: bytedepth Docker upstream is not the expected blue route.\n' >&2
        return 1
    }
}

require_blue_stack() {
    production_green_require_blue
    [[ "$(docker inspect -f '{{.State.Running}}' "$DOCKER_APP")" == true ]] || {
        printf 'Refusing: Docker blue application is not running.\n' >&2
        return 1
    }
    require_blue_route
}

backup_blue_route() {
    install -o ubuntu -g ubuntu -m 0600 "$NGINX_CONFIG" "$NGINX_BACKUP"
}

restore_blue_route() {
    [[ -f "$NGINX_BACKUP" ]] || return 0
    install -o ubuntu -g ubuntu -m 0644 "$NGINX_BACKUP" "$NGINX_CONFIG"
    docker exec "$DOCKER_NGINX" nginx -t
    docker exec "$DOCKER_NGINX" nginx -s reload
    route_changed=0
}

switch_green_route() {
    local temp nginx_dir
    nginx_dir="$(dirname "$NGINX_CONFIG")"
    temp="$(mktemp "$nginx_dir/.nginx.green.XXXXXX")"
    sed "s#proxy_pass http://$BLUE_UPSTREAM;#proxy_pass http://$GREEN_UPSTREAM;#g" \
        "$NGINX_CONFIG" > "$temp"
    install -o ubuntu -g ubuntu -m 0644 "$temp" "$NGINX_CONFIG"
    rm -f -- "$temp"
    route_changed=1
    grep -Fq "proxy_pass http://$GREEN_UPSTREAM;" "$NGINX_CONFIG" || return 1
    docker exec "$DOCKER_NGINX" nginx -t
    docker exec "$DOCKER_NGINX" nginx -s reload
}

verify_blue_public_access() {
    curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
        --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version" >/dev/null
    curl --fail --silent --show-error --retry 3 --retry-delay 2 --retry-connrefused \
        --connect-timeout 10 --max-time 60 "$PUBLIC_BASE_URL/" >/dev/null
}

verify_green_preflight() {
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE"
    curl --fail --silent --show-error --retry 30 --retry-delay 1 \
        "http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT/version" \
        | grep -F "$commit" >/dev/null
    curl --fail --silent --show-error --retry 30 --retry-delay 1 \
        "http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT/version" \
        | grep -F "$commit" >/dev/null
}

restore_blue_access() {
    local restore_status=0
    if (( route_changed )); then
        restore_blue_route || restore_status=1
    fi
    if (( blue_stopped )); then
        if docker start "$DOCKER_APP"; then
            blue_stopped=0
        else
            restore_status=1
        fi
    fi
    if ! verify_blue_public_access; then
        restore_status=1
    fi
    return "$restore_status"
}

stop_green_services() {
    systemctl stop "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE" \
        "$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE" \
        "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE" \
        "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE" \
        "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE" 2>/dev/null || true
}

rollback_on_failure() {
    local rollback_status=0
    if (( deployment_succeeded == 0 )); then
        if (( green_prepare_started )) || [[ -e "$GREEN_STATE_DIR/syncing" ]]; then
            production_green_mark_uncertain || rollback_status=1
        fi
        if [[ -n "${BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE:-}" ]]; then
            stop_green_services
        fi
        if (( rollback_required )); then
            restore_blue_access || rollback_status=1
        fi
        if (( rollback_status != 0 )); then
            printf 'Refusing: native production failure left Docker rollback verification incomplete.\n' >&2
        fi
        if (( config_changed )); then
            printf '%s\n' 'BYTEDEPTH_DEPLOY_MODE=data-access' \
                | install -o ubuntu -g ubuntu -m 0600 /dev/stdin "$CONFIG_FILE"
        fi
    fi
}
trap rollback_on_failure EXIT

ensure_production_mode "$(current_deploy_mode)"
require_blue_stack
prepare_green_config

export BYTEDEPTH_RELEASE_ROOT="$BYTEDEPTH_PRODUCTION_GREEN_RELEASE_ROOT/releases"
export BYTEDEPTH_CURRENT_LINK="$BYTEDEPTH_PRODUCTION_GREEN_RELEASE_ROOT/current"
install_release_artifact "$TAG" "$JAR" "$MANIFEST"
switch_current_release "$TAG"

production_green_require_blue
green_prepare_started=1
production_green_prepare
systemctl start "$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE" "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE"
verify_green_preflight

backup_blue_route
rollback_required=1
docker stop "$DOCKER_APP"
blue_stopped=1
production_green_final_sync
systemctl start "$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE" "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE"
production_green_verify
verify_green_preflight
switch_green_route
if ! curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
    --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version" | grep -F "$commit" >/dev/null; then
    printf 'Production deployment failed: green public version was not observed.\n' >&2
    exit 1
fi

printf 'version=%s\ncommit=%s\nartifact_sha256=%s\ndeployed_at=%s\nruntime_mode=production-native-green\n---\n' \
    "$TAG" "$commit" "$(artifact_manifest_value sha256 "$MANIFEST")" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
deployment_succeeded=1
rollback_required=0
printf 'Deployed %s (%s) through native green cutover; Docker blue baseline retained.\n' "$TAG" "$commit"
