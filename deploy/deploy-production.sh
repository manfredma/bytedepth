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

install -d -o root -g root -m 0700 "$STATE_DIR"
touch "$HISTORY_FILE"
chmod 0600 "$HISTORY_FILE"
grep -Fqx "version=$TAG" "$HISTORY_FILE" && {
    printf 'Refusing deployment: %s was already deployed on this node.\n' "$TAG" >&2
    exit 1
}

./deploy/bootstrap-ops-deploy.sh
backup_dir="$STATE_DIR/backups"
install -d -o root -g root -m 0700 "$backup_dir"
mysqladmin --protocol=socket ping >/dev/null
mysqldump --protocol=socket --all-databases --single-transaction --routines --events > "$backup_dir/mysql-$commit.sql"
chmod 0600 "$backup_dir/mysql-$commit.sql"

install_release_artifact "$TAG" "$JAR" "$MANIFEST"
switch_current_release "$TAG"
systemctl restart bytedepth-app.service
verify_running_release "$commit"
systemctl reload nginx.service

printf 'version=%s\ncommit=%s\nartifact_sha256=%s\ndeployed_at=%s\n---\n' \
    "$TAG" "$commit" "$(artifact_manifest_value sha256 "$MANIFEST")" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s) using native artifact.\n' "$TAG" "$commit"
