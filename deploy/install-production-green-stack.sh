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

export BYTEDEPTH_PRODUCTION_GREEN_CONFIG="$CONFIG_FILE"
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/production-green-target.sh"
load_production_green_target

ensure_native_dependencies() {
    local missing=()
    local nginx_service_masked=0
    command -v mysqld >/dev/null || missing+=(mysqld)
    command -v mysql >/dev/null || missing+=(mysql)
    command -v mysqladmin >/dev/null || missing+=(mysqladmin)
    command -v redis-server >/dev/null || missing+=(redis-server)
    command -v redis-cli >/dev/null || missing+=(redis-cli)
    command -v nginx >/dev/null || missing+=(nginx)
    getent passwd mysql >/dev/null || missing+=(mysql-user)
    getent group mysql >/dev/null || missing+=(mysql-group)
    getent passwd redis >/dev/null || missing+=(redis-user)
    getent group redis >/dev/null || missing+=(redis-group)
    if (( ${#missing[@]} > 0 )); then
        command -v apt-get >/dev/null || {
            printf 'Refusing: native green prerequisites are missing: %s\n' "${missing[*]}" >&2
            return 1
        }
        # The public ingress is managed separately.  nginx-core is only used
        # by the isolated green edge on 18081; prevent its package
        # maintainer scripts from starting a competing host ingress while the
        # native prerequisite is being installed.
        if [[ " ${missing[*]} " == *' nginx '* ]]; then
            systemctl mask nginx.service >/dev/null
            nginx_service_masked=1
        fi
        if ! apt-get update || ! DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
            mysql-server redis-server nginx-core; then
            if (( nginx_service_masked )); then
                systemctl unmask nginx.service >/dev/null || true
            fi
            return 1
        fi
        if (( nginx_service_masked )); then
            systemctl unmask nginx.service >/dev/null
        fi
    fi
    for command_name in mysqld mysql mysqladmin redis-server redis-cli nginx; do
        command -v "$command_name" >/dev/null || {
            printf 'Refusing: required native command is unavailable after installation: %s\n' "$command_name" >&2
            return 1
        }
    done
    for account in mysql redis; do
        getent passwd "$account" >/dev/null || {
            printf 'Refusing: required native service account is unavailable: %s\n' "$account" >&2
            return 1
        }
        getent group "$account" >/dev/null || {
            printf 'Refusing: required native service group is unavailable: %s\n' "$account" >&2
            return 1
        }
    done
}

ensure_native_dependencies

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
    "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/edge" \
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

if command -v apparmor_parser >/dev/null && [[ -f /etc/apparmor.d/usr.sbin.mysqld ]]; then
    install -d -o ubuntu -g ubuntu -m 0755 /etc/apparmor.d/local
    printf '%s\n' \
        'capability chown,' \
        "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/ r," \
        "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql/ rwk," \
        "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql/** rwk," \
        '/run/bytedepth-production-green/ r,' \
        '/run/bytedepth-production-green/** rwk,' > /etc/apparmor.d/local/usr.sbin.mysqld
    chmod 0644 /etc/apparmor.d/local/usr.sbin.mysqld
    chown ubuntu:ubuntu /etc/apparmor.d/local/usr.sbin.mysqld
    apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld
fi

chmod 0600 "$ENV_FILE" "$MEILI_ENV_FILE"
chown ubuntu:ubuntu "$ENV_FILE" "$MEILI_ENV_FILE"

printf '%s\n' 'vm.overcommit_memory = 1' > /etc/sysctl.d/99-bytedepth-production-native.conf
chmod 0644 /etc/sysctl.d/99-bytedepth-production-native.conf
chown ubuntu:ubuntu /etc/sysctl.d/99-bytedepth-production-native.conf
sysctl -w vm.overcommit_memory=1 >/dev/null

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
    "pid $BYTEDEPTH_PRODUCTION_GREEN_ROOT/edge/nginx.pid;" \
    'http {' \
    '    include /etc/nginx/mime.types;' \
    "    error_log $BYTEDEPTH_PRODUCTION_GREEN_ROOT/edge/error.log warn;" \
    "    access_log $BYTEDEPTH_PRODUCTION_GREEN_ROOT/edge/access.log;" \
    "    server { listen $BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT;" \
    '        location / {' \
    "            proxy_pass http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT;" \
    "            proxy_set_header Host \$host;" \
    "            proxy_set_header X-Real-IP \$remote_addr;" \
    "            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;" \
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
