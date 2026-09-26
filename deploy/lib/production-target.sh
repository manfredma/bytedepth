#!/usr/bin/env bash

load_production_target() {
    local config_file="${BYTEDEPTH_PRODUCTION_CONFIG:-/etc/bytedepth/production.conf}"
    [[ -r "$config_file" ]] || {
        printf 'Refusing: production configuration is missing: %s\n' "$config_file" >&2
        return 1
    }
    # shellcheck disable=SC1090
    source "$config_file"
    [[ "${BYTEDEPTH_PRODUCTION_ROOT:-}" == /data/bytedepth-native-production ]] || {
        printf 'Refusing: production production root is not isolated.\n' >&2
        return 1
    }

    local name port
    for name in \
        BYTEDEPTH_PRODUCTION_MYSQL_PORT \
        BYTEDEPTH_PRODUCTION_REDIS_PORT \
        BYTEDEPTH_PRODUCTION_MEILI_PORT \
        BYTEDEPTH_PRODUCTION_APP_PORT \
        BYTEDEPTH_PRODUCTION_EDGE_PORT; do
        port="${!name:-}"
        [[ "$port" =~ ^[1-9][0-9]{3,4}$ ]] || {
            printf 'Refusing: invalid production port: %s\n' "$name" >&2
            return 1
        }
    done
    [[ "$BYTEDEPTH_PRODUCTION_MYSQL_PORT" != 3306 \
        && "$BYTEDEPTH_PRODUCTION_REDIS_PORT" != 6379 \
        && "$BYTEDEPTH_PRODUCTION_MEILI_PORT" != 7700 \
        && "$BYTEDEPTH_PRODUCTION_APP_PORT" != 8080 \
        && "$BYTEDEPTH_PRODUCTION_EDGE_PORT" != 80 \
        && "$BYTEDEPTH_PRODUCTION_EDGE_PORT" != 443 ]] || {
        printf 'Refusing: production ports collide with default runtime ports.\n' >&2
        return 1
    }

    BYTEDEPTH_PRODUCTION_RUNTIME_MODE=production-native
    BYTEDEPTH_PRODUCTION_RELEASE_ROOT=/opt/bytedepth/production
    BYTEDEPTH_PRODUCTION_MYSQL_SERVICE=bytedepth-production-mysql.service
    BYTEDEPTH_PRODUCTION_REDIS_SERVICE=bytedepth-production-redis.service
    BYTEDEPTH_PRODUCTION_MEILI_SERVICE=bytedepth-production-meilisearch.service
    BYTEDEPTH_PRODUCTION_APP_SERVICE=bytedepth-production-app.service
    BYTEDEPTH_PRODUCTION_EDGE_SERVICE=bytedepth-production-edge.service
    BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE=bytedepth-production-public-nginx.service
    BYTEDEPTH_PRODUCTION_IMAGE_ROOT="$BYTEDEPTH_PRODUCTION_ROOT/images"
    BYTEDEPTH_PRODUCTION_HEALTH_URL="http://127.0.0.1:$BYTEDEPTH_PRODUCTION_APP_PORT"

    export BYTEDEPTH_PRODUCTION_RUNTIME_MODE \
        BYTEDEPTH_PRODUCTION_RELEASE_ROOT \
        BYTEDEPTH_PRODUCTION_MYSQL_SERVICE \
        BYTEDEPTH_PRODUCTION_REDIS_SERVICE \
        BYTEDEPTH_PRODUCTION_MEILI_SERVICE \
        BYTEDEPTH_PRODUCTION_APP_SERVICE \
        BYTEDEPTH_PRODUCTION_EDGE_SERVICE \
        BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE \
        BYTEDEPTH_PRODUCTION_IMAGE_ROOT \
        BYTEDEPTH_PRODUCTION_HEALTH_URL
}
