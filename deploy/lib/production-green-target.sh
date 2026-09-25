#!/usr/bin/env bash

load_production_green_target() {
    local config_file="${BYTEDEPTH_PRODUCTION_GREEN_CONFIG:-/etc/bytedepth/production-green.conf}"
    [[ -r "$config_file" ]] || {
        printf 'Refusing: production green configuration is missing: %s\n' "$config_file" >&2
        return 1
    }
    # shellcheck disable=SC1090
    source "$config_file"
    [[ "${BYTEDEPTH_PRODUCTION_GREEN_ROOT:-}" == /data/bytedepth-native-production ]] || {
        printf 'Refusing: production green root is not isolated.\n' >&2
        return 1
    }

    local name port
    for name in \
        BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT \
        BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT \
        BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT \
        BYTEDEPTH_PRODUCTION_GREEN_APP_PORT \
        BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT; do
        port="${!name:-}"
        [[ "$port" =~ ^[1-9][0-9]{3,4}$ ]] || {
            printf 'Refusing: invalid production green port: %s\n' "$name" >&2
            return 1
        }
    done
    [[ "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT" != 3306 \
        && "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT" != 6379 \
        && "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT" != 7700 \
        && "$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT" != 8080 \
        && "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT" != 80 \
        && "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT" != 443 ]] || {
        printf 'Refusing: production green ports collide with default runtime ports.\n' >&2
        return 1
    }

    BYTEDEPTH_PRODUCTION_GREEN_RUNTIME_MODE=production-native-green
    BYTEDEPTH_PRODUCTION_GREEN_RELEASE_ROOT=/opt/bytedepth/production-green
    BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE=bytedepth-production-green-mysql.service
    BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE=bytedepth-production-green-redis.service
    BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE=bytedepth-production-green-meilisearch.service
    BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE=bytedepth-production-green-app.service
    BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE=bytedepth-production-green-edge.service
    BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT="$BYTEDEPTH_PRODUCTION_GREEN_ROOT/images"
    BYTEDEPTH_PRODUCTION_GREEN_HEALTH_URL="http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT"

    export BYTEDEPTH_PRODUCTION_GREEN_RUNTIME_MODE \
        BYTEDEPTH_PRODUCTION_GREEN_RELEASE_ROOT \
        BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE \
        BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE \
        BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE \
        BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE \
        BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE \
        BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT \
        BYTEDEPTH_PRODUCTION_GREEN_HEALTH_URL
}
