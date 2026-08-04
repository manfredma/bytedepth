#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/deploy-release.sh vX.Y.Z\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly STATE_DIR=/var/lib/bytedepth-deploy
readonly HISTORY_FILE="$STATE_DIR/release-history"
readonly GIT_REMOTE_URL=git@github.com:manfredma/bytedepth.git
readonly TAG="${1:-}"

if [[ ! "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    printf 'Release tag must use stable SemVer, for example v1.2.3\n' >&2
    exit 1
fi

git_cmd() { git -c safe.directory="$SOURCE_ROOT" "$@"; }
cd "$SOURCE_ROOT"

if [[ "$(git_cmd remote get-url origin)" != "$GIT_REMOTE_URL" ]]; then
    printf 'Refusing deployment: origin must be %s\n' "$GIT_REMOTE_URL" >&2
    exit 1
fi

deploy_ssh_key="$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_SSH_KEY" {value=$2} END {print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ -z "$deploy_ssh_key" || ! -r "$deploy_ssh_key" ]]; then
    printf 'Refusing deployment: BYTEDEPTH_DEPLOY_SSH_KEY is missing or unreadable\n' >&2
    exit 1
fi

GIT_SSH_COMMAND="ssh -i $deploy_ssh_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new" \
    git_cmd fetch --force --no-recurse-submodules origin "refs/tags/$TAG:refs/tags/$TAG"

if [[ "$(git_cmd cat-file -t "refs/tags/$TAG" 2>/dev/null || true)" != "tag" ]]; then
    printf 'Refusing deployment: %s must be an annotated tag\n' "$TAG" >&2
    exit 1
fi

commit="$(git_cmd rev-parse "$TAG^{commit}")"
pom_version="$(git_cmd show "$commit:pom.xml" | sed -n 's@^[[:space:]]*<version>\([^<]*\)</version>[[:space:]]*$@\1@p' | head -n 1)"
if [[ "$pom_version" != "${TAG#v}" || "$pom_version" == *-SNAPSHOT ]]; then
    printf 'Refusing deployment: tag %s and Maven version %s do not match\n' "$TAG" "$pom_version" >&2
    exit 1
fi

install -d -m 0700 "$STATE_DIR"
touch "$HISTORY_FILE"
chmod 0600 "$HISTORY_FILE"
if grep -Fqx "version=$TAG" "$HISTORY_FILE"; then
    printf 'Refusing deployment: %s was already deployed on this node\n' "$TAG" >&2
    exit 1
fi

git_cmd checkout --detach "$commit"
./deploy/bootstrap-ops-deploy.sh

printf 'version=%s\ncommit=%s\ndeployed_at=%s\n---\n' \
    "$TAG" "$commit" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s)\n' "$TAG" "$commit"
