#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TARGET_ROOT=/usr/local/lib/bytedepth-deploy

install -d -m 0755 "$TARGET_ROOT" /var/lib/bytedepth-deploy
install -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-socket" "$TARGET_ROOT/bytedepth-deploy-socket"
install -m 0755 "$SOURCE_ROOT/deploy/bin/bytedepth-deploy-job" "$TARGET_ROOT/bytedepth-deploy-job"
install -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy.socket" /etc/systemd/system/bytedepth-deploy.socket
install -m 0644 "$SOURCE_ROOT/deploy/systemd/bytedepth-deploy@.service" /etc/systemd/system/bytedepth-deploy@.service

systemctl daemon-reload
systemctl enable bytedepth-deploy.socket
systemctl restart bytedepth-deploy.socket
systemctl status bytedepth-deploy.socket --no-pager
