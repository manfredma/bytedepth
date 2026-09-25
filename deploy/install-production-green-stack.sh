#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/install-production-green-stack.sh\n' >&2
    exit 1
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly SYSTEMD_DIR=/etc/systemd/system
readonly CONFIG_FILE="${BYTEDEPTH_PRODUCTION_GREEN_CONFIG:-/etc/bytedepth/production-green.conf}"
readonly ENV_FILE=/etc/bytedepth/production-green.env
readonly MEILI_ENV_FILE=/etc/bytedepth/production-green-meilisearch.env
readonly NGINX_CONFIG=/etc/bytedepth/production-green-nginx.conf

# shellcheck source=deploy/lib/production-green-target.sh
source "$SOURCE_ROOT/deploy/lib/production-green-target.sh"
load_production_green_target

[[ -r "$ENV_FILE" ]] || {
    printf 'Refusing: %s must be prepared before installing green services.\n' "$ENV_FILE" >&2
    exit 1
}
[[ -r "$MEILI_ENV_FILE" ]] || {
    printf 'Refusing: %s must be prepared before installing green services.\n' "$MEILI_ENV_FILE" >&2
    exit 1
}

getent group bytedepth >/dev/null || groupadd --system bytedepth
getent passwd bytedepth >/dev/null || useradd --system --gid bytedepth --home-dir /nonexistent --shell /usr/sbin/nologin bytedepth
getent group meilisearch >/dev/null || groupadd --system meilisearch
getent passwd meilisearch >/dev/null || useradd --system --gid meilisearch --home-dir /nonexistent --shell /usr/sbin/nologin meilisearch

install -d -o ubuntu -g ubuntu -m 0755 \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/images" \
    "$BYTEDEPTH_PRODUCTION_GREEN_RELEASE_ROOT" \
    "$BYTEDEPTH_PRODUCTION_GREEN_RELEASE_ROOT/releases"
chown ubuntu:mysql "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql"
chown ubuntu:redis "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis"
chown ubuntu:meilisearch "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch"
chown ubuntu:bytedepth "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/images"
chmod -R g+rwX \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch" \
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/images"

chmod 0600 "$ENV_FILE" "$MEILI_ENV_FILE"
chown ubuntu:ubuntu "$ENV_FILE" "$MEILI_ENV_FILE"

# shellcheck disable=SC1090
source "$ENV_FILE"
[[ "${BYTEDEPTH_ENVIRONMENT:-}" == production ]] || {
    printf 'Refusing: green environment must use BYTEDEPTH_ENVIRONMENT=production.\n' >&2
    exit 1
}
[[ "${SERVER_PORT:-}" == "$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT" ]] || {
    printf 'Refusing: green application port is not explicit.\n' >&2
    exit 1
}
[[ "${BYTEDEPTH_UPLOAD_IMAGE_DIR:-}" == "$BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT" ]] || {
    printf 'Refusing: green image directory is not isolated.\n' >&2
    exit 1
}
[[ -n "${BYTEDEPTH_REDIS_PASSWORD:-}" && -n "${BYTEDEPTH_SEARCH_API_KEY:-}" ]] || {
    printf 'Refusing: green middleware credentials are missing.\n' >&2
    exit 1
}

printf '%s\n' \
    "bind 127.0.0.1" \
    "port $BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT" \
    "dir $BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis" \
    "pidfile $BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/redis.pid" \
    'logfile ""' \
    "requirepass $BYTEDEPTH_REDIS_PASSWORD" \
    'appendonly yes' \
    'protected-mode yes' > "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/redis.conf"
chmod 0640 "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/redis.conf"
chown ubuntu:redis "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/redis.conf"

printf '%s\n' 'env = "production"' > "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch/meilisearch.toml"
chmod 0640 "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch/meilisearch.toml"
chown ubuntu:meilisearch "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch/meilisearch.toml"

printf '%s\n' \
    'events { worker_connections 1024; }' \
    'http {' \
    '    include /etc/nginx/mime.types;' \
    "    server { listen $BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT;" \
    '        location / {' \
    "            proxy_pass http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT;" \
    '            proxy_set_header Host $host;' \
    '            proxy_set_header X-Real-IP $remote_addr;' \
    '            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;' \
    '            proxy_set_header X-Forwarded-Proto https;' \
    '        }' \
    '    }' \
    '}' > "$NGINX_CONFIG"
chmod 0600 "$NGINX_CONFIG"
chown ubuntu:ubuntu "$NGINX_CONFIG"

render_unit() {
    local source="$1" target="$2"
    sed \
        -e "s#__GREEN_ROOT__#$BYTEDEPTH_PRODUCTION_GREEN_ROOT#g" \
        -e "s#__MYSQL_PORT__#$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT#g" \
        -e "s#__REDIS_PORT__#$BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT#g" \
        -e "s#__MEILI_PORT__#$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT#g" \
        -e "s#__APP_PORT__#$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT#g" \
        "$source" > "$target"
    chmod 0644 "$target"
    chown ubuntu:ubuntu "$target"
}

render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-green-mysql.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-green-redis.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-green-meilisearch.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-green-app.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-green-edge.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE"

systemctl daemon-reload
systemctl enable \
    "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE"
printf 'Installed production green native stack at %s.\n' "$BYTEDEPTH_PRODUCTION_GREEN_ROOT"
