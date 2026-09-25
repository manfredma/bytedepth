#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/install-staging-native-stack.sh\n' >&2
    exit 1
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly CONFIG_FILE=/etc/bytedepth/staging-native.conf
readonly ENV_FILE=/etc/bytedepth/staging-native.env
readonly MEILI_ENV_FILE=/etc/bytedepth/staging-native-meilisearch.env
readonly SYSTEMD_DIR=/etc/systemd/system

[[ -r "$CONFIG_FILE" ]] || {
    printf 'Refusing: %s is missing; native staging ports and paths must be explicit.\n' "$CONFIG_FILE" >&2
    exit 1
}
# shellcheck disable=SC1090
source "$CONFIG_FILE"

grep -Fqx 'BYTEDEPTH_NATIVE_STACK_MODE=parallel' "$CONFIG_FILE" || {
    printf 'Refusing: %s must explicitly select BYTEDEPTH_NATIVE_STACK_MODE=parallel.\n' "$CONFIG_FILE" >&2
    exit 1
}

native_root="${BYTEDEPTH_NATIVE_ROOT:-}"
mysql_port="${BYTEDEPTH_NATIVE_MYSQL_PORT:-}"
redis_port="${BYTEDEPTH_NATIVE_REDIS_PORT:-}"
meili_port="${BYTEDEPTH_NATIVE_MEILI_PORT:-}"
app_port="${BYTEDEPTH_NATIVE_APP_PORT:-}"
edge_port="${BYTEDEPTH_NATIVE_EDGE_PORT:-}"
for value in native_root mysql_port redis_port meili_port app_port edge_port; do
    [[ -n "${!value}" ]] || { printf 'Refusing: %s is required in %s.\n' "$value" "$CONFIG_FILE" >&2; exit 1; }
done
[[ "$native_root" == /data/bytedepth-native-staging ]] || {
    printf 'Refusing: native root is not isolated.\n' >&2
    exit 1
}
for port in "$mysql_port" "$redis_port" "$meili_port" "$app_port" "$edge_port"; do
    [[ "$port" =~ ^[1-9][0-9]{3,4}$ ]] || { printf 'Refusing: invalid native port.\n' >&2; exit 1; }
done
[[ "$mysql_port" != 3306 && "$redis_port" != 6379 && "$meili_port" != 7700 && "$app_port" != 8080 && "$edge_port" != 80 && "$edge_port" != 443 ]] || {
    printf 'Refusing: native staging ports must not collide with default runtime ports.\n' >&2
    exit 1
}

getent group bytedepth >/dev/null || groupadd --system bytedepth
getent passwd bytedepth >/dev/null || useradd --system --gid bytedepth --home-dir /nonexistent --shell /usr/sbin/nologin bytedepth
getent group meilisearch >/dev/null || groupadd --system meilisearch
getent passwd meilisearch >/dev/null || useradd --system --gid meilisearch --home-dir /nonexistent --shell /usr/sbin/nologin meilisearch

ensure_service_group_write_access() {
    local path="$1"
    chmod -R g+rwX "$path"
}

install -d -o ubuntu -g ubuntu -m 0775 "$native_root" "$native_root/mysql" "$native_root/redis" "$native_root/meilisearch" "$native_root/images"
install -d -o ubuntu -g ubuntu -m 0770 "$native_root/images-test"
chown ubuntu:mysql "$native_root/mysql"
chown ubuntu:redis "$native_root/redis"
chown ubuntu:meilisearch "$native_root/meilisearch"
chown ubuntu:bytedepth "$native_root/images"
ensure_service_group_write_access "$native_root/mysql"
ensure_service_group_write_access "$native_root/redis"
ensure_service_group_write_access "$native_root/meilisearch"
ensure_service_group_write_access "$native_root/images"
install -d -o ubuntu -g ubuntu -m 0770 /etc/bytedepth
if [[ ! -d /etc/apparmor.d/local ]]; then
    install -d -o ubuntu -g ubuntu -m 0775 /etc/apparmor.d/local
fi

# Redis warns and background persistence can fail when the kernel disables
# memory overcommit. Make this native-service prerequisite explicit and
# persistent instead of accepting the warning in deployment output.
printf '%s\n' 'vm.overcommit_memory = 1' > /etc/sysctl.d/99-bytedepth-staging-native.conf
chmod 0644 /etc/sysctl.d/99-bytedepth-staging-native.conf
chown ubuntu:ubuntu /etc/sysctl.d/99-bytedepth-staging-native.conf
sysctl -w vm.overcommit_memory=1 >/dev/null

render_unit() {
    local source="$1" target="$2"
    sed \
        -e "s#__NATIVE_ROOT__#$native_root#g" \
        -e "s#__MYSQL_PORT__#$mysql_port#g" \
        -e "s#__REDIS_PORT__#$redis_port#g" \
        -e "s#__MEILI_PORT__#$meili_port#g" \
        -e "s#__APP_PORT__#$app_port#g" \
        "$source" > "$target"
    chmod 0644 "$target"
    chown ubuntu:ubuntu "$target"
}

render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-staging-native-mysql.service.in" "$SYSTEMD_DIR/bytedepth-staging-native-mysql.service"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-staging-native-redis.service.in" "$SYSTEMD_DIR/bytedepth-staging-native-redis.service"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-staging-native-meilisearch.service.in" "$SYSTEMD_DIR/bytedepth-staging-native-meilisearch.service"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-staging-native-app.service.in" "$SYSTEMD_DIR/bytedepth-staging-native-app.service"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-staging-native-edge.service.in" "$SYSTEMD_DIR/bytedepth-staging-native-edge.service"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-staging-native-test-slot.service.in" "$SYSTEMD_DIR/bytedepth-staging-native-test-slot.service"

[[ -r "$ENV_FILE" ]] || {
    printf 'Refusing: %s must be created from migrated staging configuration before starting native app.\n' "$ENV_FILE" >&2
    exit 1
}
[[ -r "$MEILI_ENV_FILE" ]] || {
    printf 'Refusing: %s must be created before starting native Meilisearch.\n' "$MEILI_ENV_FILE" >&2
    exit 1
}
grep -Fqx 'BYTEDEPTH_ENVIRONMENT=staging' "$ENV_FILE"
grep -Fqx "SERVER_PORT=$app_port" "$ENV_FILE"
grep -Fqx "BYTEDEPTH_UPLOAD_IMAGE_DIR=$native_root/images" "$ENV_FILE"
# shellcheck disable=SC1090
source "$ENV_FILE"
[[ -n "${BYTEDEPTH_REDIS_PASSWORD:-}" ]] || {
    printf 'Refusing: native Redis password is missing from %s.\n' "$ENV_FILE" >&2
    exit 1
}
chmod 0600 "$ENV_FILE" "$MEILI_ENV_FILE"
chown ubuntu:ubuntu "$ENV_FILE" "$MEILI_ENV_FILE"

printf '%s\n' \
    'bind 127.0.0.1' \
    "port $redis_port" \
    "dir $native_root/redis" \
    "pidfile $native_root/redis/redis.pid" \
    'logfile ""' \
    "requirepass $BYTEDEPTH_REDIS_PASSWORD" \
    'maxmemory 64mb' \
    'maxmemory-policy noeviction' \
    'appendonly yes' \
    'protected-mode yes' > "$native_root/redis/redis.conf"
chmod 0640 "$native_root/redis/redis.conf"
chown ubuntu:redis "$native_root/redis/redis.conf"

if command -v apparmor_parser >/dev/null && [[ -f /etc/apparmor.d/usr.sbin.mysqld ]]; then
    printf '%s\n' \
        'capability chown,' \
        "$native_root/ r," \
        "$native_root/mysql/ rwk," \
        "$native_root/mysql/** rwk," \
        '/run/bytedepth-staging-native/ r,' \
        '/run/bytedepth-staging-native/** rwk,' > /etc/apparmor.d/local/usr.sbin.mysqld
    chmod 0644 /etc/apparmor.d/local/usr.sbin.mysqld
    chown ubuntu:ubuntu /etc/apparmor.d/local/usr.sbin.mysqld
    apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld
fi

printf '%s\n' 'env = "production"' > "$native_root/meilisearch/meilisearch.toml"
chmod 0640 "$native_root/meilisearch/meilisearch.toml"
chown ubuntu:meilisearch "$native_root/meilisearch/meilisearch.toml"

printf '%s\n' \
    'events { worker_connections 1024; }' \
    'http {' \
    '    include /etc/nginx/mime.types;' \
    "    server { listen $edge_port;" \
    '        location / {' \
    "            proxy_pass http://127.0.0.1:$app_port;" \
    '            proxy_set_header Host $host;' \
    '            proxy_set_header X-Real-IP $remote_addr;' \
    '            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;' \
    '            proxy_set_header X-Forwarded-Proto https;' \
    '        }' \
    '    }' \
    '}' > /etc/bytedepth/staging-native-nginx.conf
chmod 0600 /etc/bytedepth/staging-native-nginx.conf
chown ubuntu:ubuntu /etc/bytedepth/staging-native-nginx.conf

systemctl daemon-reload
systemctl enable bytedepth-staging-native-mysql.service bytedepth-staging-native-redis.service bytedepth-staging-native-meilisearch.service bytedepth-staging-native-app.service bytedepth-staging-native-edge.service
printf 'Installed isolated staging-native systemd stack at %s.\n' "$native_root"
