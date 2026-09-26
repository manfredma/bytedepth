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
readonly KNOWN_HOSTS_FILE="${BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS:-}"
SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly REMOTE_LOG="/tmp/bytedepth-production-${TAG}.log"
readonly SSH_TARGET="$PRODUCTION_USER@$PRODUCTION_HOST"
readonly SSH_OPTIONS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o UserKnownHostsFile="$KNOWN_HOSTS_FILE" -o StrictHostKeyChecking=yes)
ARTIFACT_DIR=""
CHECKOUT_DIR=""
LOG_SNAPSHOT_FILE="$(mktemp)"
readonly LOG_SNAPSHOT_FILE
trap 'rm -rf "$ARTIFACT_DIR" "$CHECKOUT_DIR" "$LOG_SNAPSHOT_FILE"' EXIT

# shellcheck disable=SC1091
source "$SOURCE_ROOT/deploy/lib/artifact.sh"
# shellcheck disable=SC1091
source "$SOURCE_ROOT/deploy/lib/warning-policy.sh"

validate_release_tag "$TAG" || { printf 'Release tag must use stable SemVer, for example v1.2.3\n' >&2; exit 1; }
[[ -r "$SSH_KEY" ]] || { printf 'BYTEDEPTH_PRODUCTION_SSH_KEY must name a readable SSH private key.\n' >&2; exit 1; }
[[ -n "$KNOWN_HOSTS_FILE" && -r "$KNOWN_HOSTS_FILE" ]] || { printf 'BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS must name a readable known_hosts file.\n' >&2; exit 1; }

remote() {
    # shellcheck disable=SC2029
    ssh "${SSH_OPTIONS[@]}" "$SSH_TARGET" "$@"
}

ARTIFACT_DIR="$(mktemp -d)"
readonly ARTIFACT_DIR
CHECKOUT_DIR="$(mktemp -d)"
readonly CHECKOUT_DIR

# The release Tag is an immutable, already-validated local input.  The
# production host fetches and verifies the same annotated Tag below; the local
# wrapper must not add another GitHub transport dependency before deployment.
[[ "$(git -C "$SOURCE_ROOT" cat-file -t "refs/tags/$TAG" 2>/dev/null || true)" == tag ]] || {
    printf 'Refusing deployment: %s must be an annotated tag.\n' "$TAG" >&2
    exit 1
}
commit="$(git -C "$SOURCE_ROOT" rev-parse "$TAG^{commit}")"
pom_version="$(git -C "$SOURCE_ROOT" show "$commit:pom.xml" | sed -n 's@^[[:space:]]*<version>\([^<]*\)</version>[[:space:]]*$@\1@p' | head -n 1)"
[[ "$pom_version" == "${TAG#v}" && "$pom_version" != *-SNAPSHOT ]] || {
    printf 'Refusing deployment: tag and Maven version do not match.\n' >&2
    exit 1
}
git -C "$SOURCE_ROOT" archive "$commit" | tar -x -C "$CHECKOUT_DIR"
build_release_artifact "$CHECKOUT_DIR" "$TAG" "$commit" "$ARTIFACT_DIR" "${TAG#v}"

printf 'Checking production deployment target %s...\n' "$SSH_TARGET"
preflight_output="$(remote "set -Eeuo pipefail
test -d '$REMOTE_ROOT'
sudo -n true
if sudo -n grep -Fqx 'version=$TAG' '$RELEASE_HISTORY' 2>/dev/null; then printf 'DEPLOYED\\n'; exit 20; fi
if sudo -n awk -F= '\$1 == \"state\" {state=\$2} \$1 == \"version\" {version=\$2} END {if (state == \"RUNNING\" && version == \"$TAG\") exit 0; exit 1}' '$DEPLOY_STATUS' 2>/dev/null; then printf 'BUSY\\n'; exit 21; fi
printf 'READY\\n'")" || {
    status=$?
    case "$status" in
        20) printf 'Refusing: %s was already deployed on production.\n' "$TAG" >&2 ;;
        21) printf 'Refusing: a production deployment for %s is already running.\n' "$TAG" >&2 ;;
        *) printf 'Refusing: production preflight failed; remote sudo must be non-interactive.\n' >&2 ;;
    esac
    exit 1
}
[[ "$preflight_output" == *READY* ]] || { printf 'Refusing: production preflight did not become ready.\n' >&2; exit 1; }

# The host-only deployment script is part of the immutable release input. Do
# not run an old checkout's orchestration code with a new JAR. This fetch and
# checkout happen only after duplicate/busy guards, and leave unrelated
# untracked operator files untouched.
remote "set -Eeuo pipefail
cd '$REMOTE_ROOT'
test -z \"\$(git status --porcelain=v1 --untracked-files=no)\"
git fetch --force --no-recurse-submodules origin 'refs/tags/$TAG:refs/tags/$TAG'
test \"\$(git cat-file -t 'refs/tags/$TAG')\" = tag
git checkout --detach '$TAG'
test \"\$(git rev-parse HEAD)\" = '$commit'
printf 'REMOTE_TAG_READY\\n'" >/dev/null

remote_artifact="/tmp/bytedepth-production-$TAG"
remote "$(printf 'install -d -o ubuntu -g ubuntu -m 0700 %q' "$remote_artifact")"
scp "${SSH_OPTIONS[@]}" "$ARTIFACT_DIR/app.jar" "$ARTIFACT_DIR/artifact.manifest" "$SSH_TARGET:$remote_artifact/"
printf 'Starting detached production deployment for %s; log: %s\n' "$TAG" "$REMOTE_LOG"
remote "cd '$REMOTE_ROOT' && sudo -n nohup ./deploy/deploy-production.sh --artifact '$remote_artifact/app.jar' --manifest '$remote_artifact/artifact.manifest' '$TAG' >'$REMOTE_LOG' 2>&1 </dev/null & echo \$!" >/dev/null

started_at="$(date +%s)"
while :; do
    log_snapshot="$(remote "sudo -n tail -n 80 '$REMOTE_LOG' 2>/dev/null || true")" || {
        printf 'Production polling failed; remote log: %s\n' "$REMOTE_LOG" >&2
        exit 1
    }
    printf '%s\n' "$log_snapshot" > "$LOG_SNAPSHOT_FILE"
    if ! warning_policy_check_file "$LOG_SNAPSHOT_FILE"; then
        printf '%s\n' "$log_snapshot" >&2
        printf 'Refusing: production deployment log contains an unallowlisted WARNING.\n' >&2
        exit 1
    fi
    if remote "sudo -n grep -Fqx 'version=$TAG' '$RELEASE_HISTORY'" >/dev/null 2>&1; then
        break
    fi
    if ! remote "sudo -n pgrep -af '[d]eploy-production.sh --artifact.*$TAG' >/dev/null 2>&1"; then
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
printf 'Production deployment and verification passed for %s.\n' "$TAG"
