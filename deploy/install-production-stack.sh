#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run this script with sudo: sudo ./deploy/install-production-stack.sh\n' >&2
    exit 1
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly SYSTEMD_DIR=/etc/systemd/system
readonly CONFIG_FILE="${BYTEDEPTH_PRODUCTION_CONFIG:-/etc/bytedepth/production.conf}"
readonly ENV_FILE=/etc/bytedepth/production.env
readonly MEILI_ENV_FILE=/etc/bytedepth/production-meilisearch.env
readonly NGINX_CONFIG=/etc/bytedepth/production-nginx.conf
readonly PUBLIC_NGINX_CONFIG=/etc/bytedepth/production-public-nginx.conf

export BYTEDEPTH_PRODUCTION_CONFIG="$CONFIG_FILE"
# shellcheck disable=SC1090,SC1091
source "$SOURCE_ROOT/deploy/lib/production-target.sh"
load_production_target

BYTEDEPTH_PRODUCTION_JAVA_HOME=''
BYTEDEPTH_PRODUCTION_JAVA_BIN=''

resolve_java_25() {
    local candidate version

    candidate="$(readlink -f "$(command -v java 2>/dev/null || true)" 2>/dev/null || true)"
    if [[ -x "$candidate" ]]; then
        version="$($candidate -version 2>&1 || true)"
        if [[ "$version" =~ version[[:space:]]\"25([.\"]|$) ]]; then
            BYTEDEPTH_PRODUCTION_JAVA_BIN="$candidate"
            BYTEDEPTH_PRODUCTION_JAVA_HOME="${candidate%/bin/java}"
            return 0
        fi
    fi

    for candidate in /usr/lib/jvm/*/bin/java; do
        [[ -x "$candidate" ]] || continue
        version="$($candidate -version 2>&1 || true)"
        if [[ "$version" =~ version[[:space:]]\"25([.\"]|$) ]]; then
            BYTEDEPTH_PRODUCTION_JAVA_BIN="$candidate"
            BYTEDEPTH_PRODUCTION_JAVA_HOME="${candidate%/bin/java}"
            return 0
        fi
    done
    return 1
}

ensure_java_25() {
    resolve_java_25 && return 0
    command -v apt-get >/dev/null || {
        printf 'Refusing: Java 25 is required for the native production application.\n' >&2
        return 1
    }
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-25-jre-headless
    resolve_java_25 || {
        printf 'Refusing: Java 25 is unavailable after installation.\n' >&2
        return 1
    }
}

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
            printf 'Refusing: native prerequisites are missing: %s\n' "${missing[*]}" >&2
            return 1
        }
        # The public ingress is managed separately.  nginx-core is only used
        # by the isolated production edge on 18081; prevent its package
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

ensure_java_25
ensure_native_dependencies

[[ -r "$ENV_FILE" ]] || {
    printf 'Refusing: %s must be prepared before installing production services.\n' "$ENV_FILE" >&2
    exit 1
}
[[ -r "$MEILI_ENV_FILE" ]] || {
    printf 'Refusing: %s must be prepared before installing production services.\n' "$MEILI_ENV_FILE" >&2
    exit 1
}

getent group bytedepth >/dev/null || groupadd --system bytedepth
getent passwd bytedepth >/dev/null || useradd --system --gid bytedepth --home-dir /nonexistent --shell /usr/sbin/nologin bytedepth
getent group meilisearch >/dev/null || groupadd --system meilisearch
getent passwd meilisearch >/dev/null || useradd --system --gid meilisearch --home-dir /nonexistent --shell /usr/sbin/nologin meilisearch

install -d -o ubuntu -g ubuntu -m 0755 \
    "$BYTEDEPTH_PRODUCTION_ROOT" \
    "$BYTEDEPTH_PRODUCTION_ROOT/mysql" \
    "$BYTEDEPTH_PRODUCTION_ROOT/redis" \
    "$BYTEDEPTH_PRODUCTION_ROOT/meilisearch" \
    "$BYTEDEPTH_PRODUCTION_ROOT/edge" \
    "$BYTEDEPTH_PRODUCTION_ROOT/edge/client_body_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/edge/proxy_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/edge/fastcgi_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/edge/uwsgi_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/edge/scgi_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx/client_body_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx/proxy_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx/fastcgi_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx/uwsgi_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx/scgi_temp" \
    "$BYTEDEPTH_PRODUCTION_ROOT/images" \
    "$BYTEDEPTH_PRODUCTION_RELEASE_ROOT" \
    "$BYTEDEPTH_PRODUCTION_RELEASE_ROOT/releases"
chown ubuntu:mysql "$BYTEDEPTH_PRODUCTION_ROOT/mysql"
chown ubuntu:redis "$BYTEDEPTH_PRODUCTION_ROOT/redis"
chown ubuntu:meilisearch "$BYTEDEPTH_PRODUCTION_ROOT/meilisearch"
chown ubuntu:bytedepth "$BYTEDEPTH_PRODUCTION_ROOT/images"
chmod -R g+rwX \
    "$BYTEDEPTH_PRODUCTION_ROOT/mysql" \
    "$BYTEDEPTH_PRODUCTION_ROOT/redis" \
    "$BYTEDEPTH_PRODUCTION_ROOT/meilisearch" \
    "$BYTEDEPTH_PRODUCTION_ROOT/images" \
    "$BYTEDEPTH_PRODUCTION_ROOT/public-nginx"

if command -v apparmor_parser >/dev/null && [[ -f /etc/apparmor.d/usr.sbin.mysqld ]]; then
    install -d -o ubuntu -g ubuntu -m 0755 /etc/apparmor.d/local
    printf '%s\n' \
        'capability chown,' \
        "$BYTEDEPTH_PRODUCTION_ROOT/ r," \
        "$BYTEDEPTH_PRODUCTION_ROOT/mysql/ rwk," \
        "$BYTEDEPTH_PRODUCTION_ROOT/mysql/** rwk," \
        '/run/bytedepth-production/ r,' \
        '/run/bytedepth-production/** rwk,' > /etc/apparmor.d/local/usr.sbin.mysqld
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
    printf 'Refusing: production environment must use BYTEDEPTH_ENVIRONMENT=production.\n' >&2
    exit 1
}
[[ "${SERVER_PORT:-}" == "$BYTEDEPTH_PRODUCTION_APP_PORT" ]] || {
    printf 'Refusing: production application port is not explicit.\n' >&2
    exit 1
}
[[ "${BYTEDEPTH_UPLOAD_IMAGE_DIR:-}" == "$BYTEDEPTH_PRODUCTION_IMAGE_ROOT" ]] || {
    printf 'Refusing: production image directory is not isolated.\n' >&2
    exit 1
}
[[ -n "${BYTEDEPTH_REDIS_PASSWORD:-}" && -n "${BYTEDEPTH_SEARCH_API_KEY:-}" ]] || {
    printf 'Refusing: production middleware credentials are missing.\n' >&2
    exit 1
}

printf '%s\n' \
    "bind 127.0.0.1" \
    "port $BYTEDEPTH_PRODUCTION_REDIS_PORT" \
    "dir $BYTEDEPTH_PRODUCTION_ROOT/redis" \
    "pidfile $BYTEDEPTH_PRODUCTION_ROOT/redis/redis.pid" \
    'logfile ""' \
    "requirepass $BYTEDEPTH_REDIS_PASSWORD" \
    'appendonly yes' \
    'protected-mode yes' > "$BYTEDEPTH_PRODUCTION_ROOT/redis/redis.conf"
chmod 0640 "$BYTEDEPTH_PRODUCTION_ROOT/redis/redis.conf"
chown ubuntu:redis "$BYTEDEPTH_PRODUCTION_ROOT/redis/redis.conf"

printf '%s\n' 'env = "production"' > "$BYTEDEPTH_PRODUCTION_ROOT/meilisearch/meilisearch.toml"
chmod 0640 "$BYTEDEPTH_PRODUCTION_ROOT/meilisearch/meilisearch.toml"
chown ubuntu:meilisearch "$BYTEDEPTH_PRODUCTION_ROOT/meilisearch/meilisearch.toml"

printf '%s\n' \
    'events { worker_connections 1024; }' \
    "pid $BYTEDEPTH_PRODUCTION_ROOT/edge/nginx.pid;" \
    'http {' \
    '    include /etc/nginx/mime.types;' \
    "    client_body_temp_path $BYTEDEPTH_PRODUCTION_ROOT/edge/client_body_temp;" \
    "    proxy_temp_path $BYTEDEPTH_PRODUCTION_ROOT/edge/proxy_temp;" \
    "    fastcgi_temp_path $BYTEDEPTH_PRODUCTION_ROOT/edge/fastcgi_temp;" \
    "    uwsgi_temp_path $BYTEDEPTH_PRODUCTION_ROOT/edge/uwsgi_temp;" \
    "    scgi_temp_path $BYTEDEPTH_PRODUCTION_ROOT/edge/scgi_temp;" \
    "    error_log $BYTEDEPTH_PRODUCTION_ROOT/edge/error.log warn;" \
    "    access_log $BYTEDEPTH_PRODUCTION_ROOT/edge/access.log;" \
    "    server { listen $BYTEDEPTH_PRODUCTION_EDGE_PORT;" \
    '        location / {' \
    "            proxy_pass http://127.0.0.1:$BYTEDEPTH_PRODUCTION_APP_PORT;" \
    "            proxy_set_header Host \$host;" \
    "            proxy_set_header X-Real-IP \$remote_addr;" \
    "            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;" \
    '            proxy_set_header X-Forwarded-Proto https;' \
    '        }' \
    '    }' \
    '}' > "$NGINX_CONFIG"
chmod 0600 "$NGINX_CONFIG"
chown ubuntu:ubuntu "$NGINX_CONFIG"

printf '%s\n' \
    'events { worker_connections 1024; }' \
    "pid $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/nginx.pid;" \
    'http {' \
    '    include /etc/nginx/mime.types;' \
    '    include /etc/nginx/conf.d/*.conf;' \
    "    client_body_temp_path $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/client_body_temp;" \
    "    proxy_temp_path $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/proxy_temp;" \
    "    fastcgi_temp_path $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/fastcgi_temp;" \
    "    uwsgi_temp_path $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/uwsgi_temp;" \
    "    scgi_temp_path $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/scgi_temp;" \
    "    error_log $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/error.log warn;" \
    "    access_log $BYTEDEPTH_PRODUCTION_ROOT/public-nginx/access.log;" \
    '    server {' \
    '        listen 80;' \
    '        server_name bytedepth.cn www.bytedepth.cn;' \
    "        return 301 https://\$host\$request_uri;" \
    '    }' \
    '    server {' \
    '        listen 443 ssl;' \
    '        server_name bytedepth.cn www.bytedepth.cn;' \
    '        ssl_certificate /etc/letsencrypt/live/bytedepth.cn/fullchain.pem;' \
    '        ssl_certificate_key /etc/letsencrypt/live/bytedepth.cn/privkey.pem;' \
    '        ssl_protocols TLSv1.2 TLSv1.3;' \
    '        ssl_ciphers HIGH:!aNULL:!MD5;' \
    '        client_max_body_size 10m;' \
    '        location / {' \
    "            proxy_pass http://127.0.0.1:$BYTEDEPTH_PRODUCTION_EDGE_PORT;" \
    "            proxy_set_header Host \$host;" \
    "            proxy_set_header X-Real-IP \$remote_addr;" \
    "            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;" \
    '            proxy_set_header X-Forwarded-Proto https;' \
    '        }' \
    '    }' \
    '}' > "$PUBLIC_NGINX_CONFIG"
chmod 0600 "$PUBLIC_NGINX_CONFIG"
chown ubuntu:ubuntu "$PUBLIC_NGINX_CONFIG"

render_unit() {
    local source="$1" target="$2"
    sed \
        -e "s#__PRODUCTION_ROOT__#$BYTEDEPTH_PRODUCTION_ROOT#g" \
        -e "s#__MYSQL_PORT__#$BYTEDEPTH_PRODUCTION_MYSQL_PORT#g" \
        -e "s#__REDIS_PORT__#$BYTEDEPTH_PRODUCTION_REDIS_PORT#g" \
        -e "s#__MEILI_PORT__#$BYTEDEPTH_PRODUCTION_MEILI_PORT#g" \
        -e "s#__APP_PORT__#$BYTEDEPTH_PRODUCTION_APP_PORT#g" \
        -e "s#__JAVA_HOME__#$BYTEDEPTH_PRODUCTION_JAVA_HOME#g" \
        -e "s#__JAVA_BIN__#$BYTEDEPTH_PRODUCTION_JAVA_BIN#g" \
        "$source" > "$target"
    chmod 0644 "$target"
    chown ubuntu:ubuntu "$target"
}

render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-mysql.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_MYSQL_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-redis.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_REDIS_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-meilisearch.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_MEILI_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-app.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_APP_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-edge.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_EDGE_SERVICE"
render_unit "$SOURCE_ROOT/deploy/systemd/bytedepth-production-public-nginx.service.in" "$SYSTEMD_DIR/$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE"

systemctl daemon-reload
systemctl enable \
    "$BYTEDEPTH_PRODUCTION_MYSQL_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_REDIS_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_MEILI_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_APP_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_EDGE_SERVICE" \
    "$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE"
printf 'Installed production native services at %s.\n' "$BYTEDEPTH_PRODUCTION_ROOT"
