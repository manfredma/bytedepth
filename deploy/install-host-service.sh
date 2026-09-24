#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly TARGET_ROOT=/usr/local/lib/bytedepth-deploy
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf

ensure_service_account() {
    local account="$1"
    local group="$2"
    if ! getent group "$group" >/dev/null; then
        groupadd --system "$group"
    fi
    if ! getent passwd "$account" >/dev/null; then
        useradd --system --gid "$group" --home-dir /nonexistent --shell /usr/sbin/nologin "$account"
    fi
}

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/install-host-service.sh\n' >&2
    exit 1
fi

ensure_service_account bytedepth bytedepth
ensure_service_account meilisearch meilisearch
install -d -o root -g root -m 0755 \
    "$TARGET_ROOT" /var/lib/bytedepth-deploy /opt/bytedepth /opt/bytedepth/releases \
    /etc/bytedepth /etc/bytedepth/secrets /data/images
install -d -o mysql -g mysql -m 0750 /data/mysql
install -d -o redis -g redis -m 0750 /data/redis
install -d -o meilisearch -g meilisearch -m 0750 /data/meilisearch

for unit in bytedepth-app.service mysql.service redis.service meilisearch.service nginx.service; do
    install -o root -g root -m 0644 "$SOURCE_ROOT/deploy/systemd/$unit" "/etc/systemd/system/$unit"
done
install -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-socket" "$TARGET_ROOT/bytedepth-deploy-socket"
install -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-job" "$TARGET_ROOT/bytedepth-deploy-job"
install -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy.socket" /etc/systemd/system/bytedepth-deploy.socket
install -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy@.service" /etc/systemd/system/bytedepth-deploy@.service
if [[ ! -f "$CONFIG_FILE" ]]; then
    printf 'BYTEDEPTH_DEPLOY_MODE=single-host\n' > "$CONFIG_FILE"
    chmod 0600 "$CONFIG_FILE"
fi

systemctl daemon-reload
systemctl enable mysql.service redis.service meilisearch.service bytedepth-app.service nginx.service
systemctl enable --now bytedepth-deploy.socket
