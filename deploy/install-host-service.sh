#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TARGET_ROOT=/usr/local/lib/bytedepth-deploy
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly REPOSITORY_OWNER="$(stat -c '%U' "$SOURCE_ROOT")"

repository_owner_home="$(getent passwd "$REPOSITORY_OWNER" | awk -F: '{print $6}')"
if [[ -z "$repository_owner_home" ]]; then
    printf 'Cannot determine home directory for repository owner %s\n' "$REPOSITORY_OWNER" >&2
    exit 1
fi

install -d -m 0755 "$TARGET_ROOT" /var/lib/bytedepth-deploy
install -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-socket" "$TARGET_ROOT/bytedepth-deploy-socket"
install -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-job" "$TARGET_ROOT/bytedepth-deploy-job"
install -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy.socket" /etc/systemd/system/bytedepth-deploy.socket
install -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy@.service" /etc/systemd/system/bytedepth-deploy@.service
if [[ ! -f "$CONFIG_FILE" ]]; then
    printf 'BYTEDEPTH_DEPLOY_MODE=single-host\nBYTEDEPTH_DEPLOY_SSH_KEY=%s/.ssh/id_ed25519\n' \
        "$repository_owner_home" > "$CONFIG_FILE"
    chmod 0600 "$CONFIG_FILE"
fi
if ! grep -q '^BYTEDEPTH_DEPLOY_SSH_KEY=' "$CONFIG_FILE"; then
    printf 'BYTEDEPTH_DEPLOY_SSH_KEY=%s/.ssh/id_ed25519\n' "$repository_owner_home" >> "$CONFIG_FILE"
    chmod 0600 "$CONFIG_FILE"
fi

systemctl daemon-reload
systemctl enable bytedepth-deploy.socket
systemctl restart bytedepth-deploy.socket
systemctl status bytedepth-deploy.socket --no-pager
