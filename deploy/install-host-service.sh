#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly TARGET_ROOT=/usr/local/lib/bytedepth-deploy
readonly CONFIG_FILE=/etc/bytedepth-deploy.conf
readonly DEPLOY_USER=ubuntu
readonly DEPLOY_GROUP=ubuntu

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

ensure_service_group_write_access() {
    local path="$1"
    chmod -R g+rwX "$path"
}

ensure_deployment_checkout_ownership() {
    getent passwd "$DEPLOY_USER" >/dev/null || {
        printf 'Required deployment user is missing: %s\n' "$DEPLOY_USER" >&2
        exit 1
    }
    getent group "$DEPLOY_GROUP" >/dev/null || {
        printf 'Required deployment group is missing: %s\n' "$DEPLOY_GROUP" >&2
        exit 1
    }
    chown -R "$DEPLOY_USER:$DEPLOY_GROUP" "$SOURCE_ROOT"
}

ensure_project_tree_ownership() {
    chown -R "$DEPLOY_USER:$DEPLOY_GROUP" \
        "$TARGET_ROOT" /var/lib/bytedepth-deploy /var/lib/bytedepth-staging/test-slots \
        /opt/bytedepth /etc/bytedepth /data/images /data/images-test
    chown -R "$DEPLOY_USER:mysql" /data/mysql
    chown -R "$DEPLOY_USER:redis" /data/redis
    chown -R "$DEPLOY_USER:meilisearch" /data/meilisearch
    ensure_service_group_write_access /data/mysql
    ensure_service_group_write_access /data/redis
    ensure_service_group_write_access /data/meilisearch
}

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/install-host-service.sh\n' >&2
    exit 1
fi

ensure_service_account bytedepth bytedepth
ensure_service_account meilisearch meilisearch
install -d -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0755 \
    "$TARGET_ROOT" /var/lib/bytedepth-deploy /var/lib/bytedepth-staging/test-slots \
    /opt/bytedepth /opt/bytedepth/releases /etc/bytedepth /etc/bytedepth/secrets \
    /data/images /data/images-test
install -d -o "$DEPLOY_USER" -g mysql -m 0770 /data/mysql
install -d -o "$DEPLOY_USER" -g redis -m 0770 /data/redis
install -d -o "$DEPLOY_USER" -g meilisearch -m 0770 /data/meilisearch
ensure_deployment_checkout_ownership
ensure_project_tree_ownership

for unit in bytedepth-app.service bytedepth-test-slot.service mysql.service redis.service meilisearch.service nginx.service; do
    install -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0644 "$SOURCE_ROOT/deploy/systemd/$unit" "/etc/systemd/system/$unit"
done
install -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-socket" "$TARGET_ROOT/bytedepth-deploy-socket"
install -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-job" "$TARGET_ROOT/bytedepth-deploy-job"
install -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy.socket" /etc/systemd/system/bytedepth-deploy.socket
install -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy@.service" /etc/systemd/system/bytedepth-deploy@.service
if [[ ! -f "$CONFIG_FILE" ]]; then
    printf 'BYTEDEPTH_DEPLOY_MODE=single-host\n' > "$CONFIG_FILE"
    chmod 0600 "$CONFIG_FILE"
fi
chown "$DEPLOY_USER:$DEPLOY_GROUP" "$CONFIG_FILE"

systemctl daemon-reload
systemctl enable mysql.service redis.service meilisearch.service bytedepth-app.service nginx.service
systemctl enable --now bytedepth-deploy.socket
