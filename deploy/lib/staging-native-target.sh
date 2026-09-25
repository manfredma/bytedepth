#!/usr/bin/env bash

# Resolve the service names, ports and isolated image root used by staging
# deployment and test runners. Staging must use the explicitly configured,
# isolated parallel native target; an absent or malformed marker is fatal.

load_staging_native_target() {
    local config_file="${BYTEDEPTH_STAGING_NATIVE_CONFIG:-/etc/bytedepth/staging-native.conf}"
    [[ -r "$config_file" ]] || {
        printf 'Refusing: staging native parallel configuration is incomplete.\n' >&2
        return 1
    }
    # shellcheck disable=SC1090
    source "$config_file"
    [[ "${BYTEDEPTH_NATIVE_STACK_MODE:-}" == parallel ]] || {
        printf 'Refusing: staging native parallel configuration is incomplete.\n' >&2
        return 1
    }
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

    export BYTEDEPTH_STAGING_RUNTIME_MODE BYTEDEPTH_STAGING_APP_SERVICE \
        BYTEDEPTH_STAGING_MYSQL_SERVICE BYTEDEPTH_STAGING_REDIS_SERVICE \
        BYTEDEPTH_STAGING_MEILI_SERVICE BYTEDEPTH_STAGING_EDGE_SERVICE \
        BYTEDEPTH_STAGING_TEST_SLOT_SERVICE BYTEDEPTH_STAGING_APP_PORT \
        BYTEDEPTH_STAGING_MYSQL_PORT BYTEDEPTH_STAGING_REDIS_PORT \
        BYTEDEPTH_STAGING_MEILI_PORT BYTEDEPTH_STAGING_IMAGE_ROOT \
        BYTEDEPTH_STAGING_TEST_IMAGE_ROOT BYTEDEPTH_STAGING_HEALTH_URL
}
