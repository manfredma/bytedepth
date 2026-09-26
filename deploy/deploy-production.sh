#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "${EUID}" -ne 0 ]]; then
    printf 'This is a production-host-only script. Run deploy-production-remote.sh from the development host.\n' >&2
    exit 1
fi
if [[ $# -ne 5 || "${1:-}" != --artifact || "${3:-}" != --manifest ]]; then
    printf 'Usage: sudo ./deploy/deploy-production.sh --artifact JAR --manifest MANIFEST vX.Y.Z\n' >&2
    exit 2
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly STATE_DIR=/var/lib/bytedepth-deploy
readonly HISTORY_FILE="$STATE_DIR/release-history"
readonly TAG="$5"
readonly JAR="$2"
readonly MANIFEST="$4"
readonly DEPLOY_LOCK=/var/lock/bytedepth-production-deploy.lock
readonly PUBLIC_BASE_URL=https://bytedepth.cn

exec 9>"$DEPLOY_LOCK"
flock -n 9 || { printf 'Refusing deployment: another production deployment is running.\n' >&2; exit 1; }
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/artifact.sh"
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/production-target.sh"

declare -a LEGACY_SERVICES=() LEGACY_CONFIG_FILES=() LEGACY_STATE_DIRS=()
declare -a PREVIOUS_SERVICES=()
LEGACY_RELEASE_ROOT=''
LEGACY_STATE_ROOT=''
LEGACY_CURRENT_TARGET=''
LEGACY_RELEASE_IS_ALIAS=0
PREVIOUS_APP_SERVICE=''
PREVIOUS_CONFIG_FILE=''
PREVIOUS_APP_ENV_FILE=''
PREVIOUS_MEILI_ENV_FILE=''
PREVIOUS_MYSQL_CNF=''
PREVIOUS_RELEASE_ROOT=''
layout_migration_started=0
deployment_succeeded=0

validate_release_tag "$TAG" || { printf 'Release tag must use stable SemVer.\n' >&2; exit 1; }
[[ -f "$JAR" && -f "$MANIFEST" ]] || { printf 'Uploaded artifact or manifest is missing.\n' >&2; exit 1; }
validate_artifact_manifest "$MANIFEST" "$JAR" || { printf 'Refusing deployment: invalid artifact manifest.\n' >&2; exit 1; }
[[ "$(artifact_manifest_value release_ref "$MANIFEST")" == "$TAG" ]] || {
    printf 'Refusing deployment: manifest tag does not match requested tag.\n' >&2; exit 1;
}
git_cmd() { git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" "$@"; }
[[ "$(git_cmd cat-file -t "refs/tags/$TAG" 2>/dev/null || true)" == tag ]] || {
    printf 'Refusing deployment: %s must be an annotated tag on this host.\n' "$TAG" >&2; exit 1;
}
commit="$(artifact_manifest_value commit "$MANIFEST")"
[[ "$(git_cmd rev-parse "$TAG^{commit}")" == "$commit" ]] || {
    printf 'Refusing deployment: artifact commit does not match annotated tag.\n' >&2; exit 1;
}

install -d -o ubuntu -g ubuntu -m 0700 "$STATE_DIR"
touch "$HISTORY_FILE"
chmod 0600 "$HISTORY_FILE"
chown ubuntu:ubuntu "$HISTORY_FILE"
grep -Fqx "version=$TAG" "$HISTORY_FILE" && {
    printf 'Refusing: %s was already deployed on this node.\n' "$TAG" >&2; exit 1;
}

preflight_production_baseline() {
    local candidate suffix service_file selected_service config_candidate env_candidate meili_candidate
    local current_root previous_manifest public_version
    local -a release_roots=() suffixes=(mysql redis meilisearch app edge public-nginx)
    if [[ -d /opt/bytedepth/production/releases && -L /opt/bytedepth/production/current ]]; then
        current_root=/opt/bytedepth/production
    else
        for candidate in /opt/bytedepth/production-*; do
            [[ -d "$candidate/releases" && -L "$candidate/current" ]] && release_roots+=("$candidate")
        done
        [[ ${#release_roots[@]} -eq 1 ]] || {
            printf 'Refusing: production rollback release path is missing or ambiguous.\n' >&2
            return 1
        }
        current_root="${release_roots[0]}"
    fi

    config_candidate=/etc/bytedepth/production.conf
    if [[ ! -r "$config_candidate" ]]; then
        config_candidate=''
        for candidate in /etc/bytedepth/production-*.conf; do
            [[ -r "$candidate" ]] || continue
            grep -Eq '^BYTEDEPTH_PRODUCTION_([A-Z0-9]+_)?ROOT=' "$candidate" || continue
            [[ -z "$config_candidate" ]] || {
                printf 'Refusing: production configuration source is ambiguous.\n' >&2
                return 1
            }
            config_candidate="$candidate"
        done
    fi
    [[ -n "$config_candidate" && -r "$config_candidate" ]] || {
        printf 'Refusing: production configuration is missing.\n' >&2; return 1;
    }
    PREVIOUS_CONFIG_FILE="$config_candidate"
    BYTEDEPTH_PRODUCTION_CONFIG="$config_candidate" load_production_target || return 1

    env_candidate=/etc/bytedepth/production.env
    if [[ ! -r "$env_candidate" ]]; then
        env_candidate=''
        for candidate in /etc/bytedepth/production-*.env; do
            [[ -r "$candidate" && "$candidate" != *-meilisearch.env ]] || continue
            [[ -z "$env_candidate" ]] || {
                printf 'Refusing: production application environment is ambiguous.\n' >&2
                return 1
            }
            env_candidate="$candidate"
        done
    fi
    meili_candidate=/etc/bytedepth/production-meilisearch.env
    if [[ ! -r "$meili_candidate" ]]; then
        meili_candidate=''
        for candidate in /etc/bytedepth/production-*-meilisearch.env; do
            [[ -r "$candidate" ]] || continue
            [[ -z "$meili_candidate" ]] || {
                printf 'Refusing: production search environment is ambiguous.\n' >&2
                return 1
            }
            meili_candidate="$candidate"
        done
    fi
    [[ -n "$env_candidate" && -r "$env_candidate" && -n "$meili_candidate" && -r "$meili_candidate" ]] || {
        printf 'Refusing: production service environment is incomplete.\n' >&2; return 1;
    }
    PREVIOUS_APP_ENV_FILE="$env_candidate"
    PREVIOUS_MEILI_ENV_FILE="$meili_candidate"
    for candidate in /etc/bytedepth/production-*-mysql.cnf; do
        [[ -r "$candidate" ]] || continue
        PREVIOUS_MYSQL_CNF="$candidate"
        break
    done
    ( # Validate required application settings without printing credential values.
        # shellcheck disable=SC1090
        source "$env_candidate"
        [[ "${BYTEDEPTH_ENVIRONMENT:-}" == production \
            && "${SERVER_PORT:-}" == "$BYTEDEPTH_PRODUCTION_APP_PORT" \
            && "${BYTEDEPTH_UPLOAD_IMAGE_DIR:-}" == "$BYTEDEPTH_PRODUCTION_ROOT/images" \
            && -n "${BYTEDEPTH_REDIS_PASSWORD:-}" \
            && -n "${BYTEDEPTH_SEARCH_API_KEY:-}" ]]
    ) || { printf 'Refusing: production application settings are incomplete.\n' >&2; return 1; }

    BYTEDEPTH_RELEASE_ROOT="$current_root/releases"
    BYTEDEPTH_CURRENT_LINK="$current_root/current"
    PREVIOUS_RELEASE_ROOT="$current_root"
    previous_release="$(current_release_path)"
    [[ "$previous_release" == "$current_root/releases/v"* \
        && -f "$previous_release/app.jar" && -f "$previous_release/artifact.manifest" ]] || {
        printf 'Refusing: current production rollback artifact is missing.\n' >&2; return 1;
    }
    previous_manifest="$previous_release/artifact.manifest"
    validate_artifact_manifest "$previous_manifest" "$previous_release/app.jar" || {
        printf 'Refusing: current production rollback manifest is invalid.\n' >&2; return 1;
    }
    previous_commit="$(artifact_manifest_value commit "$previous_manifest")"
    previous_version="$(artifact_manifest_value application_version "$previous_manifest")"

    for suffix in "${suffixes[@]}"; do
        selected_service=""
        service_file="/etc/systemd/system/bytedepth-production-$suffix.service"
        if systemctl is-active --quiet "bytedepth-production-$suffix.service"; then
            selected_service="bytedepth-production-$suffix.service"
        else
            for service_file in /etc/systemd/system/bytedepth-production-*-"$suffix".service; do
                [[ -e "$service_file" ]] || continue
                candidate="${service_file##*/}"
                if systemctl is-active --quiet "$candidate"; then
                    selected_service="$candidate"
                    break
                fi
            done
        fi
        [[ -n "$selected_service" ]] || {
            printf 'Refusing: current production baseline is not fully active: %s\n' "$suffix" >&2
            return 1
        }
        PREVIOUS_SERVICES+=("$selected_service")
        if [[ "$suffix" == app ]]; then
            PREVIOUS_APP_SERVICE="$selected_service"
            BYTEDEPTH_PRODUCTION_APP_SERVICE="$selected_service"
        fi
    done
    BYTEDEPTH_HEALTH_URL="http://127.0.0.1:$BYTEDEPTH_PRODUCTION_APP_PORT" \
        BYTEDEPTH_APP_SERVICE="$BYTEDEPTH_PRODUCTION_APP_SERVICE" \
        verify_running_release "$previous_commit" "$previous_version" || {
            printf 'Refusing: current production application is not healthy.\n' >&2; return 1;
        }
    public_version="$(curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
        --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version")" || return 1
    jq --exit-status --arg commit "$previous_commit" --arg version "$previous_version" \
        '.commitId == $commit and .version == $version' <<< "$public_version" >/dev/null || {
            printf 'Refusing: public route does not serve the current rollback release.\n' >&2; return 1;
        }
}

normalize_live_layout() {
    local old_service service suffix config_candidate candidate migration_required=0
    local -a suffixes=(mysql redis meilisearch app edge public-nginx)
    local -a old_release_roots=()
    for suffix in "${suffixes[@]}"; do
        for unit_file in /etc/systemd/system/bytedepth-production-*-"$suffix".service; do
            [[ -e "$unit_file" ]] || continue
            old_service="${unit_file##*/}"
            [[ "$old_service" == "bytedepth-production-$suffix.service" ]] && continue
            systemctl cat "$old_service" >/dev/null 2>&1 && LEGACY_SERVICES+=("$old_service")
        done
    done
    for candidate in /opt/bytedepth/production-*; do
        [[ -d "$candidate" && -L "$candidate/current" && -d "$candidate/releases" ]] && old_release_roots+=("$candidate")
    done
    for candidate in /var/lib/bytedepth-production-*; do
        [[ -d "$candidate" && ( -e "$candidate/prepared" || -e "$candidate/verified" ) ]] && LEGACY_STATE_DIRS+=("$candidate")
    done
    if [[ -e /var/lib/bytedepth-production && ${#LEGACY_STATE_DIRS[@]} -gt 0 ]]; then
        printf 'Refusing: both normalized and transitional production state directories exist.\n' >&2
        return 1
    fi
    if [[ ! -e /var/lib/bytedepth-production && ${#LEGACY_STATE_DIRS[@]} -gt 1 ]]; then
        printf 'Refusing: production state path migration is ambiguous.\n' >&2
        return 1
    fi
    if [[ ! -e /var/lib/bytedepth-production && ${#LEGACY_STATE_DIRS[@]} -eq 1 ]]; then
        LEGACY_STATE_ROOT="${LEGACY_STATE_DIRS[0]}"
    fi
    if [[ ! -e /opt/bytedepth/production && ${#old_release_roots[@]} -gt 1 ]]; then
        printf 'Refusing: production release path migration is ambiguous.\n' >&2
        return 1
    fi
    if [[ ! -e /opt/bytedepth/production && ${#old_release_roots[@]} -eq 1 ]]; then
        local previous_link normalized_target
        LEGACY_RELEASE_ROOT="${old_release_roots[0]}"
        previous_link="$LEGACY_RELEASE_ROOT/current"
        LEGACY_CURRENT_TARGET="$(readlink -- "$previous_link")"
        migration_required=1
    elif [[ -e /opt/bytedepth/production && ${#old_release_roots[@]} -gt 0 ]]; then
        [[ ${#old_release_roots[@]} -eq 1 && -L "${old_release_roots[0]}" \
            && "$(readlink -f -- "${old_release_roots[0]}")" == /opt/bytedepth/production ]] || {
            printf 'Refusing: production release path has a conflicting legacy directory.\n' >&2
            return 1
        }
        LEGACY_RELEASE_ROOT="${old_release_roots[0]}"
        LEGACY_RELEASE_IS_ALIAS=1
        migration_required=1
    fi
    for candidate in /etc/bytedepth/production-*.conf /etc/bytedepth/production-*.env /etc/bytedepth/production-*.cnf /etc/bytedepth/production-*-nginx.conf; do
        [[ -f "$candidate" ]] || continue
        [[ "$candidate" == /etc/bytedepth/production.conf \
            || "$candidate" == /etc/bytedepth/production.env \
            || "$candidate" == /etc/bytedepth/production-meilisearch.env \
            || "$candidate" == /etc/bytedepth/production-mysql.cnf \
            || "$candidate" == /etc/bytedepth/production-nginx.conf \
            || "$candidate" == /etc/bytedepth/production-public-nginx.conf ]] || migration_required=1
    done
    if [[ ${#LEGACY_SERVICES[@]} -gt 0 || -n "$LEGACY_STATE_ROOT" ]]; then
        migration_required=1
    fi
    if (( migration_required )); then
        layout_migration_started=1
        for service in "${PREVIOUS_SERVICES[@]}"; do systemctl stop "$service"; done
        for service in "${LEGACY_SERVICES[@]}"; do
            systemctl disable "$service"
        done
    fi
    if [[ -n "$LEGACY_RELEASE_ROOT" && "$LEGACY_RELEASE_IS_ALIAS" -eq 0 ]]; then
        local previous_link normalized_target
        previous_link="$LEGACY_RELEASE_ROOT/current"
        mv -- "${old_release_roots[0]}" /opt/bytedepth/production
        ln -s -- /opt/bytedepth/production "$LEGACY_RELEASE_ROOT"
        normalized_target="$LEGACY_CURRENT_TARGET"
        if [[ "$LEGACY_CURRENT_TARGET" == "$LEGACY_RELEASE_ROOT/"* ]]; then
            normalized_target="/opt/bytedepth/production/${LEGACY_CURRENT_TARGET#"$LEGACY_RELEASE_ROOT/"}"
        fi
        ln -sfnT -- "$normalized_target" /opt/bytedepth/production/current
        chown -h ubuntu:ubuntu /opt/bytedepth/production/current
    fi
    if [[ -n "$LEGACY_STATE_ROOT" ]]; then
        mv -- "$LEGACY_STATE_ROOT" /var/lib/bytedepth-production
        chown -R ubuntu:ubuntu /var/lib/bytedepth-production
    fi
    if [[ "$PREVIOUS_CONFIG_FILE" != /etc/bytedepth/production.conf ]]; then
        normalize_production_config_file "$PREVIOUS_CONFIG_FILE" /etc/bytedepth/production.conf
        LEGACY_CONFIG_FILES+=("$PREVIOUS_CONFIG_FILE")
    fi
    if [[ "$PREVIOUS_APP_ENV_FILE" != /etc/bytedepth/production.env ]]; then
        install -o ubuntu -g ubuntu -m 0600 "$PREVIOUS_APP_ENV_FILE" /etc/bytedepth/production.env
        LEGACY_CONFIG_FILES+=("$PREVIOUS_APP_ENV_FILE")
    fi
    if [[ "$PREVIOUS_MEILI_ENV_FILE" != /etc/bytedepth/production-meilisearch.env ]]; then
        install -o ubuntu -g ubuntu -m 0600 "$PREVIOUS_MEILI_ENV_FILE" /etc/bytedepth/production-meilisearch.env
        LEGACY_CONFIG_FILES+=("$PREVIOUS_MEILI_ENV_FILE")
    fi
    if [[ -n "$PREVIOUS_MYSQL_CNF" ]]; then
        install -o ubuntu -g ubuntu -m 0600 "$PREVIOUS_MYSQL_CNF" /etc/bytedepth/production-mysql.cnf
        LEGACY_CONFIG_FILES+=("$PREVIOUS_MYSQL_CNF")
    fi
    for config_candidate in /etc/bytedepth/production-*.conf /etc/bytedepth/production-*.env \
        /etc/bytedepth/production-*.cnf /etc/bytedepth/production-*-nginx.conf; do
        [[ -f "$config_candidate" ]] || continue
        case "$config_candidate" in
            /etc/bytedepth/production.conf|/etc/bytedepth/production.env|\
                /etc/bytedepth/production-meilisearch.env|/etc/bytedepth/production-mysql.cnf|\
                /etc/bytedepth/production-nginx.conf|/etc/bytedepth/production-public-nginx.conf)
                continue
                ;;
        esac
        LEGACY_CONFIG_FILES+=("$config_candidate")
    done
    systemctl daemon-reload
}

rollback_layout_on_exit() {
    local original_status=$? rollback_status=0 suffix service public_version
    trap - EXIT
    if (( layout_migration_started && ! deployment_succeeded )); then
        set +e
        for suffix in mysql redis meilisearch app edge public-nginx; do
            service="bytedepth-production-$suffix.service"
            if systemctl cat "$service" >/dev/null 2>&1; then
                systemctl stop "$service" || rollback_status=1
                systemctl disable "$service" || rollback_status=1
            fi
        done
        if [[ -n "$LEGACY_RELEASE_ROOT" && -d /opt/bytedepth/production && ! -e "$LEGACY_RELEASE_ROOT" ]]; then
            ln -s -- /opt/bytedepth/production "$LEGACY_RELEASE_ROOT" || rollback_status=1
        fi
        if [[ -n "$LEGACY_STATE_ROOT" && -d /var/lib/bytedepth-production && ! -e "$LEGACY_STATE_ROOT" ]]; then
            mv -- /var/lib/bytedepth-production "$LEGACY_STATE_ROOT" || rollback_status=1
            chown -R ubuntu:ubuntu "$LEGACY_STATE_ROOT" || rollback_status=1
        fi
        BYTEDEPTH_PRODUCTION_RELEASE_ROOT="$PREVIOUS_RELEASE_ROOT"
        BYTEDEPTH_RELEASE_ROOT="$PREVIOUS_RELEASE_ROOT/releases"
        BYTEDEPTH_CURRENT_LINK="$PREVIOUS_RELEASE_ROOT/current"
        restore_current_release "$previous_release" || rollback_status=1
        systemctl daemon-reload || rollback_status=1
        for service in "${PREVIOUS_SERVICES[@]}"; do
            systemctl enable "$service" || rollback_status=1
            systemctl start "$service" || rollback_status=1
        done
        BYTEDEPTH_HEALTH_URL="http://127.0.0.1:${BYTEDEPTH_PRODUCTION_APP_PORT:-0}" \
            BYTEDEPTH_APP_SERVICE="$PREVIOUS_APP_SERVICE" \
            verify_running_release "$previous_commit" "$previous_version" || rollback_status=1
        public_version="$(curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
            --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version")" || rollback_status=1
        if [[ -n "$public_version" ]]; then
            jq --exit-status --arg commit "$previous_commit" --arg version "$previous_version" \
                '.commitId == $commit and .version == $version' <<< "$public_version" >/dev/null || rollback_status=1
        fi
        if (( rollback_status != 0 )); then
            printf 'Deployment failed and the previous production services could not be fully restored.\n' >&2
        else
            printf 'Deployment failed; restored the previous production services and release.\n' >&2
        fi
    fi
    exit "$original_status"
}

preflight_production_baseline
trap rollback_layout_on_exit EXIT
normalize_live_layout
[[ -f /etc/bytedepth/production.conf && -f /etc/bytedepth/production.env && \
   -f /etc/bytedepth/production-meilisearch.env ]] || {
    printf 'Refusing: production native configuration is incomplete.\n' >&2; exit 1;
}
export BYTEDEPTH_PRODUCTION_CONFIG=/etc/bytedepth/production.conf
load_production_target
export BYTEDEPTH_RELEASE_ROOT="$BYTEDEPTH_PRODUCTION_RELEASE_ROOT/releases"
export BYTEDEPTH_CURRENT_LINK="$BYTEDEPTH_PRODUCTION_RELEASE_ROOT/current"

previous_release="$(current_release_path)"
[[ -f "$previous_release/app.jar" && -f "$previous_release/artifact.manifest" ]] || {
    printf 'Refusing: current native rollback release is missing.\n' >&2; exit 1;
}
validate_artifact_manifest "$previous_release/artifact.manifest" "$previous_release/app.jar" || {
    printf 'Refusing: current native rollback manifest is invalid.\n' >&2; exit 1;
}
previous_commit="$(artifact_manifest_value commit "$previous_release/artifact.manifest")"
previous_version="$(artifact_manifest_value application_version "$previous_release/artifact.manifest")"

restore_previous_release() {
    local restored_public_version
    restore_current_release "$previous_release" || return 1
    systemctl restart "$BYTEDEPTH_PRODUCTION_APP_SERVICE" || return 1
    BYTEDEPTH_HEALTH_URL="$BYTEDEPTH_PRODUCTION_HEALTH_URL" \
        BYTEDEPTH_APP_SERVICE="$BYTEDEPTH_PRODUCTION_APP_SERVICE" \
        verify_running_release "$previous_commit" "$previous_version" || return 1
    systemctl reload "$BYTEDEPTH_PRODUCTION_EDGE_SERVICE" || return 1
    systemctl reload "$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE" || return 1
    restored_public_version="$(curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
        --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version")" || return 1
    jq --exit-status --arg commit "$previous_commit" --arg version "$previous_version" \
        '.commitId == $commit and .version == $version' <<< "$restored_public_version" >/dev/null
}

BYTEDEPTH_PRODUCTION_ROOT="$BYTEDEPTH_PRODUCTION_ROOT" "$SOURCE_ROOT/deploy/install-production-stack.sh"
systemctl daemon-reload
systemctl start "$BYTEDEPTH_PRODUCTION_MYSQL_SERVICE" "$BYTEDEPTH_PRODUCTION_REDIS_SERVICE" "$BYTEDEPTH_PRODUCTION_MEILI_SERVICE"
systemctl start "$BYTEDEPTH_PRODUCTION_APP_SERVICE" "$BYTEDEPTH_PRODUCTION_EDGE_SERVICE" "$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE"
for service in "$BYTEDEPTH_PRODUCTION_MYSQL_SERVICE" "$BYTEDEPTH_PRODUCTION_REDIS_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_MEILI_SERVICE" "$BYTEDEPTH_PRODUCTION_APP_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_EDGE_SERVICE" "$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE"; do
    systemctl is-active --quiet "$service" || { printf 'Production service is not active: %s\n' "$service" >&2; exit 1; }
done
current_public_version="$(curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
    --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version")"
jq --exit-status --arg commit "$previous_commit" --arg version "$previous_version" \
    '.commitId == $commit and .version == $version' <<< "$current_public_version" >/dev/null || {
    printf 'Refusing: public route does not serve the current rollback release.\n' >&2; exit 1;
}

install_release_artifact "$TAG" "$JAR" "$MANIFEST"
switch_current_release "$TAG"
if ! systemctl restart "$BYTEDEPTH_PRODUCTION_APP_SERVICE" || \
    ! BYTEDEPTH_HEALTH_URL="$BYTEDEPTH_PRODUCTION_HEALTH_URL" \
        BYTEDEPTH_APP_SERVICE="$BYTEDEPTH_PRODUCTION_APP_SERVICE" \
        verify_running_release "$commit" "${TAG#v}" || \
    ! systemctl reload "$BYTEDEPTH_PRODUCTION_EDGE_SERVICE" || \
    ! systemctl reload "$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE"; then
    if ! restore_previous_release; then
        printf 'Deployment failed and rollback verification is incomplete.\n' >&2
        exit 1
    fi
    printf 'Deployment failed; restored the previous native release.\n' >&2
    exit 1
fi
public_version="$(curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
    --connect-timeout 10 --max-time 120 "$PUBLIC_BASE_URL/version")"
jq --exit-status --arg commit "$commit" --arg version "${TAG#v}" \
    '.commitId == $commit and .version == $version' <<< "$public_version" >/dev/null || {
    if ! restore_previous_release; then
        printf 'Public release verification failed and rollback verification is incomplete.\n' >&2
        exit 1
    fi
    printf 'Public release verification failed; restored the previous native release.\n' >&2
    exit 1
}
for service in "${LEGACY_SERVICES[@]}"; do
    systemctl disable "$service"
    rm -f -- "/etc/systemd/system/$service"
    if [[ -d "/etc/systemd/system/$service.d" ]]; then
        rm -rf -- "/etc/systemd/system/$service.d"
    fi
done
for candidate in "${LEGACY_CONFIG_FILES[@]}"; do
    [[ -f "$candidate" ]] && rm -f -- "$candidate"
done
if [[ -n "$LEGACY_RELEASE_ROOT" && -L "$LEGACY_RELEASE_ROOT" ]]; then
    [[ "$(readlink -- "$LEGACY_RELEASE_ROOT")" == /opt/bytedepth/production ]] || {
        printf 'Refusing: compatibility path no longer points to the normalized production root.\n' >&2
        exit 1
    }
    unlink -- "$LEGACY_RELEASE_ROOT"
fi
systemctl daemon-reload
for candidate in /var/lib/bytedepth-production-*; do
    [[ -d "$candidate" && ( -e "$candidate/prepared" || -e "$candidate/verified" ) ]] && {
        printf 'Refusing: legacy production state directory remains after migration: %s\n' "$candidate" >&2
        exit 1
    }
done
layout_migration_started=0
deployment_succeeded=1
printf 'version=%s\ncommit=%s\nartifact_sha256=%s\ndeployed_at=%s\nruntime_mode=production-native\n---\n' \
    "$TAG" "$commit" "$(artifact_manifest_value sha256 "$MANIFEST")" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s) through the native production service.\n' "$TAG" "$commit"
