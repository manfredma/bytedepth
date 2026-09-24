#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# One-time staging blue/green migration.  The existing container stack remains
# the source and rollback target until switch has completed and the owner has
# accepted the native stack.  This script is the only deployment asset allowed
# to inspect or stop the old container stack.

if [[ ${EUID} -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/migrate-staging-docker-to-native.sh {prepare|switch|rollback|cleanup}\n' >&2
    exit 1
fi
[[ $# -eq 1 && $1 =~ ^(prepare|switch|rollback|cleanup)$ ]] || {
    printf 'Usage: %s {prepare|switch|rollback|cleanup}\n' "$0" >&2
    exit 2
}

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly STATE_DIR=/var/lib/bytedepth-staging/native-migration
readonly ROOT_CONFIG=/etc/bytedepth/staging-native.conf
readonly APP_ENV=/etc/bytedepth/staging-native.env
readonly MEILI_ENV=/etc/bytedepth/staging-native-meilisearch.env
readonly MYSQL_ADMIN=/etc/bytedepth/staging-native-mysql-admin.cnf
readonly DOT_ENV=/opt/bytedepth/.env
readonly DOCKER_MYSQL=bytedepth-mysql-1
readonly DOCKER_REDIS=bytedepth-redis-1
readonly DOCKER_MEILI=bytedepth-meilisearch-1
readonly DOCKER_APP=bytedepth-bytedepth-app-1
readonly DOCKER_NGINX=bytedepth-nginx-1
readonly NGINX_ROUTE=/opt/nginx-conf.d/default.conf
readonly OLD_MYSQL_DUMP="$STATE_DIR/mysql-final.sql"
readonly OLD_REDIS_DUMP="$STATE_DIR/redis-final.rdb"
readonly OLD_MEILI_SNAPSHOT="$STATE_DIR/meilisearch-final.snapshot"
readonly NGINX_BACKUP="$STATE_DIR/default.conf.before-native"
readonly SWITCH_MARKER="$STATE_DIR/switched"
readonly CLEANUP_MARKER="$STATE_DIR/cleanup-accepted"

source "$SOURCE_ROOT/deploy/lib/staging-native-target.sh"
load_staging_native_target
[[ "$BYTEDEPTH_STAGING_RUNTIME_MODE" == host-native-parallel ]] || {
    printf 'Refusing: staging-native parallel configuration is not enabled.\n' >&2
    exit 1
}

install -d -o root -g root -m 0700 "$STATE_DIR"

env_value() {
    local file="$1" key="$2"
    awk -F= -v wanted="$key" '$1 == wanted {value = substr($0, index($0, "=") + 1)} END {print value}' "$file"
}

docker_env_value() {
    local container="$1" key="$2"
    docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$container" \
        | awk -F= -v wanted="$key" '$1 == wanted {value = substr($0, index($0, "=") + 1)} END {print value}'
}

sql_escape() { printf '%s' "$1" | sed "s/'/''/g"; }

require_docker_stack() {
    command -v docker >/dev/null
    for container in "$DOCKER_MYSQL" "$DOCKER_REDIS" "$DOCKER_MEILI" "$DOCKER_APP" "$DOCKER_NGINX"; do
        docker inspect "$container" >/dev/null
    done
    [[ -f "$NGINX_ROUTE" && ! -L "$NGINX_ROUTE" ]] || {
        printf 'Refusing: shared Nginx route file is missing.\n' >&2
        return 1
    }
}

require_native_assets() {
    for file in "$ROOT_CONFIG" "$APP_ENV" "$MEILI_ENV"; do
        [[ -f "$file" && ! -L "$file" ]] || {
            printf 'Refusing: native configuration is missing: %s\n' "$file" >&2
            return 1
        }
    done
    grep -Fqx 'BYTEDEPTH_NATIVE_STACK_MODE=parallel' "$ROOT_CONFIG"
    grep -Fqx 'BYTEDEPTH_ENVIRONMENT=staging' "$APP_ENV"
    grep -Fqx "SERVER_PORT=$BYTEDEPTH_STAGING_APP_PORT" "$APP_ENV"
    grep -Fqx "BYTEDEPTH_UPLOAD_IMAGE_DIR=$BYTEDEPTH_STAGING_IMAGE_ROOT" "$APP_ENV"
}

start_native_data() {
    systemctl start "$BYTEDEPTH_STAGING_MYSQL_SERVICE" "$BYTEDEPTH_STAGING_REDIS_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_MYSQL_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_REDIS_SERVICE"
}

native_mysql_socket_exec() {
    mysql --protocol=socket --socket=/run/bytedepth-staging-native/mysql.sock -uroot "$@"
}

native_mysql_tcp_exec() {
    mysql --defaults-extra-file="$MYSQL_ADMIN" "$@"
}

wait_for_native_mysql() {
    for _ in {1..60}; do
        if [[ -f "$MYSQL_ADMIN" ]]; then
            native_mysql_tcp_exec --batch --skip-column-names -e 'SELECT 1' >/dev/null 2>&1 && return 0
        elif mysqladmin --protocol=socket --socket=/run/bytedepth-staging-native/mysql.sock -uroot ping >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    printf 'Refusing: native MySQL did not become ready.\n' >&2
    return 1
}

initialize_native_mysql() {
    local data_dir="$BYTEDEPTH_NATIVE_ROOT/mysql"
    if [[ ! -f "$data_dir/auto.cnf" ]]; then
        systemctl stop "$BYTEDEPTH_STAGING_MYSQL_SERVICE" 2>/dev/null || true
        [[ -d "$data_dir" && ! -L "$data_dir" ]] || {
            printf 'Refusing: native MySQL data path is not a real directory.\n' >&2
            return 1
        }
        [[ -z "$(find "$data_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]] || {
            printf 'Refusing: native MySQL data path is not empty.\n' >&2
            return 1
        }
        rmdir -- "$data_dir"
        chown mysql:mysql "$BYTEDEPTH_NATIVE_ROOT"
        if ! mysqld --initialize-insecure --user=mysql --datadir="$data_dir" --log-error="$data_dir/error.log" --lower-case-table-names=1; then
            chown root:root "$BYTEDEPTH_NATIVE_ROOT"
            return 1
        fi
        chown root:root "$BYTEDEPTH_NATIVE_ROOT"
        chown mysql:mysql "$data_dir"
    fi
}

write_native_application_env() {
    local db_password redis_password meili_key remember_key temp
    db_password="$(env_value "$DOT_ENV" DB_PASSWORD)"
    redis_password="$(env_value "$DOT_ENV" REDIS_PASSWORD)"
    meili_key="$(env_value "$DOT_ENV" MEILI_MASTER_KEY)"
    remember_key="$(env_value "$DOT_ENV" BYTEDEPTH_REMEMBER_ME_KEY)"
    [[ -n "$db_password" && -n "$redis_password" && -n "$meili_key" && -n "$remember_key" ]] || {
        printf 'Refusing: required existing staging secrets are missing from %s.\n' "$DOT_ENV" >&2
        return 1
    }
    temp="$(mktemp /etc/bytedepth/.staging-native.env.XXXXXX)"
    printf '%s\n' \
        'BYTEDEPTH_ENVIRONMENT=staging' \
        'BYTEDEPTH_DOMAIN=staging-bytedepth.bytedepth.cn' \
        'BYTEDEPTH_SITE_URL=https://staging-bytedepth.bytedepth.cn' \
        'SPRING_PROFILES_ACTIVE=staging' \
        "SERVER_PORT=$BYTEDEPTH_STAGING_APP_PORT" \
        "BYTEDEPTH_DATASOURCE_URL=jdbc:mysql://127.0.0.1:$BYTEDEPTH_STAGING_MYSQL_PORT/bytedepth?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true" \
        'BYTEDEPTH_DATASOURCE_USERNAME=bytedepth' \
        "BYTEDEPTH_DATASOURCE_PASSWORD=$db_password" \
        'SPRING_DATA_REDIS_HOST=127.0.0.1' \
        "SPRING_DATA_REDIS_PORT=$BYTEDEPTH_STAGING_REDIS_PORT" \
        'BYTEDEPTH_REDIS_DATABASE=0' \
        "BYTEDEPTH_REDIS_PASSWORD=$redis_password" \
        'BYTEDEPTH_REDIS_SESSION_NAMESPACE=bytedepth:session:v2' \
        'BYTEDEPTH_REDIS_KEY_NAMESPACE=bytedepth:runtime:v2' \
        "BYTEDEPTH_SEARCH_URL=http://127.0.0.1:$BYTEDEPTH_STAGING_MEILI_PORT" \
        "BYTEDEPTH_SEARCH_API_KEY=$meili_key" \
        'BYTEDEPTH_SEARCH_INDEX=posts' \
        "BYTEDEPTH_UPLOAD_IMAGE_DIR=$BYTEDEPTH_STAGING_IMAGE_ROOT" \
        'BYTEDEPTH_SESSION_COOKIE_SECURE=true' \
        "BYTEDEPTH_REMEMBER_ME_KEY=$remember_key" \
        'BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE=true' > "$temp"
    chown root:root "$temp"
    chmod 0600 "$temp"
    mv -f "$temp" "$APP_ENV"
    printf 'MEILI_MASTER_KEY=%s\n' "$meili_key" > "$MEILI_ENV"
    chown root:root "$MEILI_ENV"
    chmod 0600 "$MEILI_ENV"
}

migrate_mysql() {
    local source_password db_password escaped_password
    source_password="$(docker_env_value "$DOCKER_MYSQL" MYSQL_ROOT_PASSWORD)"
    db_password="$(env_value "$DOT_ENV" DB_PASSWORD)"
    [[ -n "$source_password" && -n "$db_password" ]] || return 1
    mkdir -p "$STATE_DIR"
    docker exec -e MYSQL_PWD="$source_password" "$DOCKER_MYSQL" \
        mysqldump -uroot --databases bytedepth --single-transaction --routines --events --triggers --no-tablespaces > "$OLD_MYSQL_DUMP"
    chmod 0600 "$OLD_MYSQL_DUMP"
    initialize_native_mysql
    systemctl start "$BYTEDEPTH_STAGING_MYSQL_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_MYSQL_SERVICE"
    escaped_password="$(sql_escape "$db_password")"
    wait_for_native_mysql
    if [[ -f "$MYSQL_ADMIN" ]]; then
        native_mysql_tcp_exec -e 'DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
        native_mysql_tcp_exec < "$OLD_MYSQL_DUMP"
    else
        native_mysql_socket_exec -e 'DROP DATABASE IF EXISTS bytedepth; CREATE DATABASE bytedepth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
        native_mysql_socket_exec < "$OLD_MYSQL_DUMP"
        native_mysql_socket_exec -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$escaped_password'; CREATE USER IF NOT EXISTS 'bytedepth'@'localhost' IDENTIFIED BY '$escaped_password'; ALTER USER 'bytedepth'@'localhost' IDENTIFIED BY '$escaped_password'; GRANT ALL PRIVILEGES ON bytedepth.* TO 'bytedepth'@'localhost'; FLUSH PRIVILEGES;"
        printf '[client]\nhost=127.0.0.1\nport=%s\nuser=root\npassword=%s\nprotocol=tcp\n' "$BYTEDEPTH_STAGING_MYSQL_PORT" "$db_password" > "$MYSQL_ADMIN"
        chown root:root "$MYSQL_ADMIN"
        chmod 0600 "$MYSQL_ADMIN"
    fi
    native_mysql_tcp_exec --batch --skip-column-names -e 'SELECT 1' >/dev/null
}

migrate_redis() {
    local redis_password="$1"
    systemctl stop "$BYTEDEPTH_STAGING_REDIS_SERVICE" 2>/dev/null || true
    docker exec -e REDISCLI_AUTH="$redis_password" "$DOCKER_REDIS" redis-cli --rdb /tmp/bytedepth-native-staging.rdb >/dev/null
    docker cp "$DOCKER_REDIS:/tmp/bytedepth-native-staging.rdb" "$OLD_REDIS_DUMP"
    docker exec "$DOCKER_REDIS" rm -f /tmp/bytedepth-native-staging.rdb
    chmod 0600 "$OLD_REDIS_DUMP"
    rm -f -- "$BYTEDEPTH_NATIVE_ROOT/redis/dump.rdb"
    rm -rf -- "$BYTEDEPTH_NATIVE_ROOT/redis/appendonlydir"
    install -o redis -g redis -m 0640 "$OLD_REDIS_DUMP" "$BYTEDEPTH_NATIVE_ROOT/redis/dump.rdb"
    systemctl start "$BYTEDEPTH_STAGING_REDIS_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_REDIS_SERVICE"
}

ensure_meilisearch_runtime() {
    if [[ ! -x /lib/ld-musl-x86_64.so.1 ]]; then
        command -v apt-get >/dev/null || {
            printf 'Refusing: musl loader is missing and apt-get is unavailable.\n' >&2
            return 1
        }
        apt-get update
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends musl
    fi
    install -d -o root -g root -m 0755 /usr/lib/x86_64-linux-musl
    if [[ ! -r /usr/lib/x86_64-linux-musl/libgcc_s.so.1 ]]; then
        docker exec "$DOCKER_MEILI" /bin/sh -c 'cat /usr/lib/libgcc_s.so.1' \
            > /tmp/libgcc_s.musl.so.1
        install -o root -g root -m 0644 /tmp/libgcc_s.musl.so.1 \
            /usr/lib/x86_64-linux-musl/libgcc_s.so.1
        rm -f /tmp/libgcc_s.musl.so.1
    fi
}

migrate_meilisearch() {
    local meili_key task status snapshot docker_meili_version
    meili_key="$(env_value "$DOT_ENV" MEILI_MASTER_KEY)"
    [[ -n "$meili_key" ]] || return 1
    ensure_meilisearch_runtime
    if [[ ! -x /usr/local/bin/meilisearch ]]; then
        docker_meili_version="$(docker exec "$DOCKER_MEILI" /bin/meilisearch --version)"
        if [[ "$docker_meili_version" == *'1.7.6'* ]]; then
            docker exec "$DOCKER_MEILI" /bin/sh -c 'cat /bin/meilisearch' > /tmp/meilisearch-1.7.6-linux-amd64
        else
            curl -fL --retry 3 --connect-timeout 10 \
                https://github.com/meilisearch/meilisearch/releases/download/v1.7.6/meilisearch-linux-amd64 \
                -o /tmp/meilisearch-1.7.6-linux-amd64
        fi
        install -o root -g root -m 0755 /tmp/meilisearch-1.7.6-linux-amd64 /usr/local/bin/meilisearch
        rm -f /tmp/meilisearch-1.7.6-linux-amd64
    fi
    /usr/local/bin/meilisearch --version | grep -Fq '1.7.6'
    task="$(curl --fail --silent --show-error -X POST -H "Authorization: Bearer $meili_key" http://127.0.0.1:7700/snapshots | jq -er '.taskUid')"
    for _ in {1..60}; do
        status="$(curl --fail --silent --show-error -H "Authorization: Bearer $meili_key" "http://127.0.0.1:7700/tasks/$task" | jq -er '.status')"
        [[ "$status" == succeeded ]] && break
        [[ "$status" == failed ]] && return 1
        sleep 2
    done
    [[ "$status" == succeeded ]] || return 1
    snapshot="$(find /data/meilisearch/snapshots -maxdepth 1 -type f -name '*.snapshot' -print | sort | tail -n 1)"
    [[ -n "$snapshot" && -f "$snapshot" ]] || return 1
    install -o root -g root -m 0600 "$snapshot" "$OLD_MEILI_SNAPSHOT"
    systemctl stop "$BYTEDEPTH_STAGING_MEILI_SERVICE" 2>/dev/null || true
    rm -rf -- "$BYTEDEPTH_NATIVE_ROOT/meilisearch"/*
    install -d -o meilisearch -g meilisearch -m 0750 "$BYTEDEPTH_NATIVE_ROOT/meilisearch"
    timeout 180 /usr/local/bin/meilisearch --import-snapshot "$OLD_MEILI_SNAPSHOT" --db-path "$BYTEDEPTH_NATIVE_ROOT/meilisearch"
    chown -R meilisearch:meilisearch "$BYTEDEPTH_NATIVE_ROOT/meilisearch"
    systemctl start "$BYTEDEPTH_STAGING_MEILI_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_MEILI_SERVICE"
}

migrate_images() {
    rsync -a --delete /data/images/ "$BYTEDEPTH_STAGING_IMAGE_ROOT/"
    chown -R bytedepth:bytedepth "$BYTEDEPTH_STAGING_IMAGE_ROOT"
}

prepare() {
    require_docker_stack
    [[ -f "$ROOT_CONFIG" && ! -L "$ROOT_CONFIG" ]] || {
        printf 'Refusing: %s is missing.\n' "$ROOT_CONFIG" >&2
        return 1
    }
    write_native_application_env
    require_native_assets
    "$SOURCE_ROOT/deploy/install-staging-native-stack.sh"
    local redis_password
    redis_password="$(env_value "$DOT_ENV" REDIS_PASSWORD)"
    migrate_mysql
    migrate_redis "$redis_password"
    migrate_meilisearch
    migrate_images
    touch "$STATE_DIR/prepared"
    printf 'Prepared isolated staging-native data and services; public traffic is unchanged.\n'
}

switch_traffic() {
    require_docker_stack
    require_native_assets
    [[ -f "$STATE_DIR/prepared" ]] || { printf 'Refusing: run prepare first.\n' >&2; return 1; }
    grep -Fq 'bytedepth-app:8080' "$NGINX_ROUTE" || {
        printf 'Refusing: shared Nginx route does not contain the expected bytedepth Docker upstream.\n' >&2
        return 1
    }
    local redis_password
    redis_password="$(env_value "$DOT_ENV" REDIS_PASSWORD)"
    cp -a -- "$NGINX_ROUTE" "$NGINX_BACKUP"
    docker stop "$DOCKER_APP"
    migrate_mysql
    migrate_redis "$redis_password"
    migrate_meilisearch
    migrate_images
    systemctl start "$BYTEDEPTH_STAGING_APP_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_APP_SERVICE"
    systemctl start "$BYTEDEPTH_STAGING_EDGE_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_EDGE_SERVICE"
    sed -i "s#bytedepth-app:8080#172.18.0.1:$BYTEDEPTH_NATIVE_EDGE_PORT#g" "$NGINX_ROUTE"
    grep -Fq "172.18.0.1:$BYTEDEPTH_NATIVE_EDGE_PORT" "$NGINX_ROUTE"
    docker exec "$DOCKER_NGINX" nginx -t
    docker exec "$DOCKER_NGINX" nginx -s reload
    touch "$SWITCH_MARKER"
    printf 'Switched staging public traffic to the isolated native stack. Docker app is stopped and retained for rollback.\n'
}

rollback() {
    require_docker_stack
    [[ -f "$NGINX_BACKUP" ]] || { printf 'Refusing: no Nginx rollback backup exists.\n' >&2; return 1; }
    cp -a -- "$NGINX_BACKUP" "$NGINX_ROUTE"
    docker exec "$DOCKER_NGINX" nginx -t
    docker exec "$DOCKER_NGINX" nginx -s reload
    systemctl stop "$BYTEDEPTH_STAGING_EDGE_SERVICE" "$BYTEDEPTH_STAGING_APP_SERVICE" 2>/dev/null || true
    docker start "$DOCKER_APP"
    rm -f -- "$SWITCH_MARKER"
    printf 'Rolled staging traffic back to the retained container stack.\n'
}

cleanup() {
    require_docker_stack
    [[ -f "$SWITCH_MARKER" ]] || { printf 'Refusing: native traffic switch has not been recorded.\n' >&2; return 1; }
    [[ "${BYTEDEPTH_NATIVE_CLEANUP_ACCEPTED:-0}" == 1 ]] || {
        printf 'Refusing: set BYTEDEPTH_NATIVE_CLEANUP_ACCEPTED=1 only after owner acceptance.\n' >&2
        return 1
    }
    systemctl is-active --quiet "$BYTEDEPTH_STAGING_APP_SERVICE"
    docker rm -f "$DOCKER_APP" "$DOCKER_MYSQL" "$DOCKER_REDIS" "$DOCKER_MEILI"
    rm -r -- /data/mysql /data/redis /data/meilisearch
    touch "$CLEANUP_MARKER"
    printf 'Removed the old bytedepth containers and their old data roots; shared Nginx and other projects were retained.\n'
}

case "$1" in
    prepare) prepare ;;
    switch) switch_traffic ;;
    rollback) rollback ;;
    cleanup) cleanup ;;
esac
