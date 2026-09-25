#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'This is a production-host-only script. From the local checkout run:\n' >&2
    printf 'BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" ./deploy/deploy-production-remote.sh vX.Y.Z\n' >&2
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
readonly TAG="$5"
readonly JAR="$2"
readonly MANIFEST="$4"
readonly DEPLOY_LOCK=/var/lock/bytedepth-production-deploy.lock

exec 9>"$DEPLOY_LOCK"
flock -n 9 || { printf 'Refusing deployment: another production deployment is running.\n' >&2; exit 1; }

source "$SOURCE_ROOT/deploy/lib/artifact.sh"

validate_release_tag "$TAG" || { printf 'Release tag must use stable SemVer.\n' >&2; exit 1; }
[[ -f "$JAR" && -f "$MANIFEST" ]] || { printf 'Uploaded artifact or manifest is missing.\n' >&2; exit 1; }
[[ "$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value=$2} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)" == production ]] || {
    printf 'Refusing deployment: BYTEDEPTH_DEPLOY_MODE must be production.\n' >&2
    exit 1
}
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

install -d -o ubuntu -g ubuntu -m 0700 "$STATE_DIR"
touch "$HISTORY_FILE"
chmod 0600 "$HISTORY_FILE"
chown ubuntu:ubuntu "$HISTORY_FILE"
grep -Fqx "version=$TAG" "$HISTORY_FILE" && {
    printf 'Refusing deployment: %s was already deployed on this node.\n' "$TAG" >&2
    exit 1
}

previous_release_path="$(current_release_path)"
release_switched=0
rollback_release() {
    if (( release_switched == 0 )); then
        return 0
    fi
    if [[ -n "$previous_release_path" ]]; then
        restore_current_release "$previous_release_path" || return 1
        systemctl restart bytedepth-app.service || return 1
        systemctl reload nginx.service || return 1
    else
        systemctl stop bytedepth-app.service || return 1
    fi
}
fail_deployment() {
    local reason="$1"
    printf 'Production deployment failed during %s.\n' "$reason" >&2
    if ! rollback_release; then
        printf 'Refusing: production deployment failed and native rollback also failed.\n' >&2
    fi
    exit 1
}

./deploy/bootstrap-ops-deploy.sh
backup_dir="$STATE_DIR/backups"
    install -d -o ubuntu -g ubuntu -m 0700 "$backup_dir"
mysqladmin --protocol=socket ping >/dev/null
mysqldump --protocol=socket --all-databases --single-transaction --routines --events > "$backup_dir/mysql-$commit.sql"
chown ubuntu:ubuntu "$backup_dir/mysql-$commit.sql"
chmod 0600 "$backup_dir/mysql-$commit.sql"

install_release_artifact "$TAG" "$JAR" "$MANIFEST"
switch_current_release "$TAG" || fail_deployment release_switch
release_switched=1
systemctl restart bytedepth-app.service || fail_deployment app_restart
verify_running_release "$commit" || fail_deployment app_health
systemctl reload nginx.service || fail_deployment nginx_reload

printf 'version=%s\ncommit=%s\nartifact_sha256=%s\ndeployed_at=%s\n---\n' \
    "$TAG" "$commit" "$(artifact_manifest_value sha256 "$MANIFEST")" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s) using native artifact.\n' "$TAG" "$commit"
