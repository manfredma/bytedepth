#!/usr/bin/env bash
set -Eeuo pipefail

readonly PRODUCTION_USER=ubuntu
readonly PRODUCTION_HOST=175.24.197.202
readonly REMOTE_ROOT=/opt/bytedepth
readonly RELEASE_HISTORY=/var/lib/bytedepth-deploy/release-history
readonly DEPLOY_STATUS=/var/lib/bytedepth-deploy/status
readonly POLL_INTERVAL_SECONDS=10
readonly POLL_TIMEOUT_SECONDS=3600
readonly TAG="${1:-}"
readonly SSH_KEY="${BYTEDEPTH_PRODUCTION_SSH_KEY:-}"
readonly KNOWN_HOSTS_FILE="${BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS:-${HOME}/.ssh/known_hosts}"

if [[ ! "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    printf 'Release tag must use stable SemVer, for example v1.2.3\n' >&2
    exit 1
fi
if [[ -z "$SSH_KEY" || ! -r "$SSH_KEY" ]]; then
    printf 'BYTEDEPTH_PRODUCTION_SSH_KEY must name a readable SSH private key.\n' >&2
    exit 1
fi
if [[ ! -r "$KNOWN_HOSTS_FILE" ]]; then
    printf 'BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS must name a readable known_hosts file.\n' >&2
    exit 1
fi

readonly REMOTE_LOG="/tmp/bytedepth-production-${TAG}.log"
readonly SSH_TARGET="$PRODUCTION_USER@$PRODUCTION_HOST"
readonly SSH_OPTIONS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o UserKnownHostsFile="$KNOWN_HOSTS_FILE" -o StrictHostKeyChecking=yes)
readonly LOG_SNAPSHOT_FILE="$(mktemp)"
trap 'rm -f "$LOG_SNAPSHOT_FILE"' EXIT
source "$(cd "$(dirname "$0")" && pwd)/lib/warning-policy.sh"

remote() {
    ssh "${SSH_OPTIONS[@]}" "$SSH_TARGET" "$@"
}

printf 'Checking production deployment target %s...\n' "$SSH_TARGET"
preflight_output="$(remote "set -Eeuo pipefail
test -d '$REMOTE_ROOT'
sudo -n true
if sudo -n grep -Fqx 'version=$TAG' '$RELEASE_HISTORY' 2>/dev/null; then
    printf 'DEPLOYED\\n'
    exit 20
fi
if sudo -n awk -F= '\$1 == \"state\" {state=\$2} \$1 == \"version\" {version=\$2} END {if (state == \"RUNNING\" && version == \"$TAG\") exit 0; exit 1}' '$DEPLOY_STATUS' 2>/dev/null; then
    printf 'BUSY\\n'
    exit 21
fi
if sudo -n pgrep -af '[d]eploy-production.sh $TAG' >/dev/null 2>&1; then
    printf 'BUSY\\n'
    exit 21
fi
printf 'READY\\n'")" || {
    status=$?
    case "$status" in
        20) printf 'Refusing: %s was already deployed on production.\n' "$TAG" >&2 ;;
        21) printf 'Refusing: a production deployment for %s is already running.\n' "$TAG" >&2 ;;
        *) printf 'Refusing: production preflight failed; remote sudo must be non-interactive.\n' >&2 ;;
    esac
    exit 1
}

if [[ "$preflight_output" != *READY* ]]; then
    printf 'Refusing: production preflight did not become ready.\n' >&2
    exit 1
fi

printf 'Starting detached production deployment for %s; log: %s\n' "$TAG" "$REMOTE_LOG"
remote "cd '$REMOTE_ROOT' && sudo -n nohup ./deploy/deploy-production.sh '$TAG' >'$REMOTE_LOG' 2>&1 </dev/null & echo \$!" >/dev/null

started_at="$(date +%s)"
while :; do
    log_snapshot="$(remote "sudo -n tail -n 80 '$REMOTE_LOG' 2>/dev/null || true")" || {
        printf 'Production polling failed; remote log: %s\n' "$REMOTE_LOG" >&2
        exit 1
    }
    printf '%s\n' "$log_snapshot" > "$LOG_SNAPSHOT_FILE"
    if ! warning_policy_check_file "$LOG_SNAPSHOT_FILE" >/dev/null; then
        printf '%s\n' "$log_snapshot" >&2
        printf 'Refusing: production deployment log contains an unallowlisted WARNING; remote log: %s\n' "$REMOTE_LOG" >&2
        exit 1
    fi
    if remote "sudo -n grep -Fqx 'version=$TAG' '$RELEASE_HISTORY'" >/dev/null 2>&1; then
        break
    fi
    if ! remote "sudo -n pgrep -af '[d]eploy-production.sh $TAG' >/dev/null 2>&1"; then
        printf '%s\n' "$log_snapshot" >&2
        printf 'Production deployment failed before recording %s; remote log: %s\n' "$TAG" "$REMOTE_LOG" >&2
        exit 1
    fi
    if (( $(date +%s) - started_at >= POLL_TIMEOUT_SECONDS )); then
        printf '%s\n' "$log_snapshot" >&2
        printf 'Production deployment timed out after %s seconds; remote log: %s\n' "$POLL_TIMEOUT_SECONDS" "$REMOTE_LOG" >&2
        exit 1
    fi
    sleep "$POLL_INTERVAL_SECONDS"
done

printf 'Production deploy recorded %s. Running read-only verification...\n' "$TAG"
remote "cd '$REMOTE_ROOT' && sudo -n ./scripts/verify-production-release.sh '$TAG'"
printf 'Production deployment and verification passed for %s. Remote log: %s\n' "$TAG" "$REMOTE_LOG"
