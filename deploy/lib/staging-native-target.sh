#!/usr/bin/env bash

# Resolve the service names, ports and isolated image root used by staging
# deployment and test runners.  The parallel target is intentionally selected
# only by an explicit ubuntu-owned marker; an absent or malformed marker keeps
# the legacy native target and never silently guesses an endpoint.

load_staging_native_target() {
    local config_file="${BYTEDEPTH_STAGING_NATIVE_CONFIG:-/etc/bytedepth/staging-native.conf}"
    local mode='canonical'

    if [[ -r "$config_file" ]]; then
        # shellcheck disable=SC1090
        source "$config_file"
        mode="${BYTEDEPTH_NATIVE_STACK_MODE:-canonical}"
    fi

    case "$mode" in
        parallel)
            [[ "${BYTEDEPTH_NATIVE_ROOT:-}" == /data/bytedepth-native-staging ]] || {
                printf 'Refusing: parallel staging target has no isolated native root.\n' >&2
                return 1
            }
            BYTEDEPTH_STAGING_RUNTIME_MODE=host-native-parallel
            BYTEDEPTH_STAGING_APP_SERVICE=bytedepth-staging-native-app.service
            BYTEDEPTH_STAGING_MYSQL_SERVICE=bytedepth-staging-native-mysql.service
            BYTEDEPTH_STAGING_REDIS_SERVICE=bytedepth-staging-native-redis.service
            BYTEDEPTH_STAGING_MEILI_SERVICE=bytedepth-staging-native-meilisearch.service
            BYTEDEPTH_STAGING_EDGE_SERVICE=bytedepth-staging-native-edge.service
            BYTEDEPTH_STAGING_TEST_SLOT_SERVICE=bytedepth-staging-native-test-slot.service
            BYTEDEPTH_STAGING_APP_PORT="${BYTEDEPTH_NATIVE_APP_PORT:?BYTEDEPTH_NATIVE_APP_PORT is required}"
            BYTEDEPTH_STAGING_MYSQL_PORT="${BYTEDEPTH_NATIVE_MYSQL_PORT:?BYTEDEPTH_NATIVE_MYSQL_PORT is required}"
            BYTEDEPTH_STAGING_REDIS_PORT="${BYTEDEPTH_NATIVE_REDIS_PORT:?BYTEDEPTH_NATIVE_REDIS_PORT is required}"
            BYTEDEPTH_STAGING_MEILI_PORT="${BYTEDEPTH_NATIVE_MEILI_PORT:?BYTEDEPTH_NATIVE_MEILI_PORT is required}"
            BYTEDEPTH_STAGING_IMAGE_ROOT="$BYTEDEPTH_NATIVE_ROOT/images"
            BYTEDEPTH_STAGING_TEST_IMAGE_ROOT="$BYTEDEPTH_NATIVE_ROOT/images-test"
            BYTEDEPTH_STAGING_HEALTH_URL="http://127.0.0.1:$BYTEDEPTH_STAGING_APP_PORT"
            ;;
        canonical)
            BYTEDEPTH_STAGING_RUNTIME_MODE=host-native
            BYTEDEPTH_STAGING_APP_SERVICE=bytedepth-app.service
            BYTEDEPTH_STAGING_MYSQL_SERVICE=mysql.service
            BYTEDEPTH_STAGING_REDIS_SERVICE=redis.service
            BYTEDEPTH_STAGING_MEILI_SERVICE=meilisearch.service
            BYTEDEPTH_STAGING_EDGE_SERVICE=nginx.service
            BYTEDEPTH_STAGING_TEST_SLOT_SERVICE=bytedepth-test-slot.service
            BYTEDEPTH_STAGING_APP_PORT=8080
            BYTEDEPTH_STAGING_MYSQL_PORT=3306
            BYTEDEPTH_STAGING_REDIS_PORT=6379
            BYTEDEPTH_STAGING_MEILI_PORT=7700
            BYTEDEPTH_STAGING_IMAGE_ROOT=/data/images
            BYTEDEPTH_STAGING_TEST_IMAGE_ROOT=/data/images-test
            BYTEDEPTH_STAGING_HEALTH_URL=http://127.0.0.1:8080
            ;;
        *)
            printf 'Refusing: unsupported BYTEDEPTH_NATIVE_STACK_MODE=%s.\n' "$mode" >&2
            return 1
            ;;
    esac

    export BYTEDEPTH_STAGING_RUNTIME_MODE BYTEDEPTH_STAGING_APP_SERVICE \
        BYTEDEPTH_STAGING_MYSQL_SERVICE BYTEDEPTH_STAGING_REDIS_SERVICE \
        BYTEDEPTH_STAGING_MEILI_SERVICE BYTEDEPTH_STAGING_EDGE_SERVICE \
        BYTEDEPTH_STAGING_TEST_SLOT_SERVICE BYTEDEPTH_STAGING_APP_PORT \
        BYTEDEPTH_STAGING_MYSQL_PORT BYTEDEPTH_STAGING_REDIS_PORT \
        BYTEDEPTH_STAGING_MEILI_PORT BYTEDEPTH_STAGING_IMAGE_ROOT \
        BYTEDEPTH_STAGING_TEST_IMAGE_ROOT BYTEDEPTH_STAGING_HEALTH_URL
}
