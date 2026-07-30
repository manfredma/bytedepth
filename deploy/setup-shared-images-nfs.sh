#!/usr/bin/env bash
set -Eeuo pipefail

readonly IMAGE_DIR=/data/images
readonly MOUNT_DIR=/mnt/bytedepth-images
readonly IMAGE_UID=10001

usage() {
    printf 'Usage: sudo %s data-node <application-private-ip> | app-node <data-private-ip>\n' "$0" >&2
    exit 64
}

if [[ "${EUID}" -ne 0 || "$#" -ne 2 ]]; then
    usage
fi

role="$1"
peer_ip="$2"

case "$role" in
    data-node)
        apt-get update
        DEBIAN_FRONTEND=noninteractive apt-get install -y nfs-kernel-server
        install -d -m 0755 "$IMAGE_DIR"
        chown -R "$IMAGE_UID:$IMAGE_UID" "$IMAGE_DIR"
        install -d -m 0755 /etc/exports.d
        printf '%s %s(rw,sync,no_subtree_check,all_squash,anonuid=%s,anongid=%s)\n' \
            "$IMAGE_DIR" "$peer_ip" "$IMAGE_UID" "$IMAGE_UID" > /etc/exports.d/bytedepth-images.exports
        exportfs -ra
        systemctl enable --now nfs-server
        exportfs -v
        ;;
    app-node)
        apt-get update
        DEBIAN_FRONTEND=noninteractive apt-get install -y nfs-common
        install -d -m 0755 "$MOUNT_DIR"
        fstab_entry="$peer_ip:$IMAGE_DIR $MOUNT_DIR nfs4 rw,_netdev,nofail 0 0"
        if ! grep -qxF "$fstab_entry" /etc/fstab; then
            printf '%s\n' "$fstab_entry" >> /etc/fstab
        fi
        if ! mountpoint -q "$MOUNT_DIR"; then
            mount "$MOUNT_DIR"
        fi
        install -d -m 0755 /etc/systemd/system/docker.service.d
        cat > /etc/systemd/system/docker.service.d/bytedepth-images.conf <<'EOF'
[Unit]
RequiresMountsFor=/mnt/bytedepth-images
After=remote-fs.target
EOF
        systemctl daemon-reload
        systemctl restart docker
        mountpoint -q "$MOUNT_DIR"
        findmnt -no SOURCE,FSTYPE,TARGET "$MOUNT_DIR"
        ;;
    *)
        usage
        ;;
esac
