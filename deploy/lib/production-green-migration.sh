#!/usr/bin/env bash

production_green_sql_escape() {
    printf '%s' "$1" | sed "s/'/''/g"
}

production_green_docker_env_value() {
    local container="$1" key="$2"
    docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$container" \
        | awk -F= -v wanted="$key" '$1 == wanted {value = substr($0, index($0, "=") + 1)} END {print value}'
}

production_green_require_blue() {
    local container state
    command -v docker >/dev/null || { printf 'Refusing: Docker is required for the blue source stack.\n' >&2; return 1; }
    command -v jq >/dev/null || { printf 'Refusing: jq is required for production green migration.\n' >&2; return 1; }
    for container in \
        bytedepth-mysql-1 \
        bytedepth-redis-1 \
        bytedepth-meilisearch-1 \
        bytedepth-bytedepth-app-1 \
        bytedepth-nginx-1; do
        docker inspect "$container" >/dev/null 2>&1 || {
            printf 'Refusing: expected blue container is missing: %s\n' "$container" >&2
            return 1
        }
    done
    for container in bytedepth-mysql-1 bytedepth-redis-1 bytedepth-meilisearch-1; do
        state="$(docker inspect -f '{{.State.Running}}' "$container")"
        [[ "$state" == true ]] || {
            printf 'Refusing: blue data service is not running: %s\n' "$container" >&2
            return 1
        }
    done
}

production_green_require_target() {
    [[ -n "${BYTEDEPTH_PRODUCTION_GREEN_ROOT:-}" ]] || {
        printf 'Refusing: production green target is not loaded.\n' >&2
        return 1
    }
    install -d -o ubuntu -g ubuntu -m 0700 "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT"
    [[ ! -e "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/uncertain" ]] || {
        printf 'Refusing: previous green migration is uncertain; manual recovery is required.\n' >&2
        return 1
    }
}

production_green_write_app_env() {
    local db_password redis_password meili_key remember_key temp
    install -d -o ubuntu -g ubuntu -m 0700 /etc/bytedepth
    db_password="$(production_green_docker_env_value bytedepth-mysql-1 MYSQL_ROOT_PASSWORD)"
    redis_password="$(production_green_docker_env_value bytedepth-redis-1 REDISCLI_AUTH)"
    meili_key="$(production_green_docker_env_value bytedepth-meilisearch-1 MEILI_MASTER_KEY)"
    remember_key="$(production_green_docker_env_value bytedepth-bytedepth-app-1 BYTEDEPTH_REMEMBER_ME_KEY)"
    [[ -n "$db_password" && -n "$redis_password" && -n "$meili_key" && -n "$remember_key" ]] || {
        printf 'Refusing: required blue credentials are missing.\n' >&2
        return 1
    }
    temp="$(mktemp /etc/bytedepth/.production-green.env.XXXXXX)"
    chown ubuntu:ubuntu "$temp"
    printf '%s\n' \
        'BYTEDEPTH_ENVIRONMENT=production' \
        'BYTEDEPTH_DOMAIN=bytedepth.cn' \
        'BYTEDEPTH_SITE_URL=https://bytedepth.cn' \
        'SPRING_PROFILES_ACTIVE=production' \
        "SERVER_PORT=$BYTEDEPTH_PRODUCTION_GREEN_APP_PORT" \
        "BYTEDEPTH_DATASOURCE_URL=jdbc:mysql://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT/bytedepth?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true" \
        'BYTEDEPTH_DATASOURCE_USERNAME=root' \
        "BYTEDEPTH_DATASOURCE_PASSWORD=$db_password" \
        'SPRING_DATA_REDIS_HOST=127.0.0.1' \
        "SPRING_DATA_REDIS_PORT=$BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT" \
        'BYTEDEPTH_REDIS_DATABASE=0' \
        "BYTEDEPTH_REDIS_PASSWORD=$redis_password" \
        'BYTEDEPTH_REDIS_SESSION_NAMESPACE=bytedepth:session:v2' \
        'BYTEDEPTH_REDIS_KEY_NAMESPACE=bytedepth:runtime:v2' \
        "BYTEDEPTH_SEARCH_URL=http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT" \
        "BYTEDEPTH_SEARCH_API_KEY=$meili_key" \
        'BYTEDEPTH_SEARCH_INDEX=posts' \
        "BYTEDEPTH_UPLOAD_IMAGE_DIR=$BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT" \
        'BYTEDEPTH_GEOIP_DB_PATH=/data/geoip/GeoLite2-City.mmdb' \
        'BYTEDEPTH_SESSION_COOKIE_SECURE=true' \
        "BYTEDEPTH_REMEMBER_ME_KEY=$remember_key" \
        'BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE=true' > "$temp"
    chmod 0600 "$temp"
    mv -f "$temp" /etc/bytedepth/production-green.env
    chown ubuntu:ubuntu /etc/bytedepth/production-green.env
    printf 'MEILI_MASTER_KEY=%s\n' "$meili_key" > /etc/bytedepth/production-green-meilisearch.env
    chmod 0600 /etc/bytedepth/production-green-meilisearch.env
    chown ubuntu:ubuntu /etc/bytedepth/production-green-meilisearch.env
}

production_green_prepare_mysql_dump() {
    local force="${1:-false}"
    local dump_file="$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/mysql.sql.gz"
    local dump_log="$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/mysql.dump.log"
    local db_password
    db_password="$(production_green_docker_env_value bytedepth-mysql-1 MYSQL_ROOT_PASSWORD)"
    install -d -o ubuntu -g ubuntu -m 0700 "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial"
    if [[ "$force" == true || ! -f "$dump_file" ]]; then
        docker exec -e MYSQL_PWD="$db_password" bytedepth-mysql-1 \
            mysqldump -uroot --databases bytedepth --single-transaction --quick \
            --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF \
            2> "$dump_log" | gzip -1 > "$dump_file" || {
                touch "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/uncertain"
                chown ubuntu:ubuntu "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/uncertain"
                printf 'Refusing: bounded MySQL dump failed; migration is uncertain.\n' >&2
                return 1
            }
        if grep -Eqi '\bWARN(ING)?\b|\bERROR\b' "$dump_log"; then
            touch "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/uncertain"
            chown ubuntu:ubuntu "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/uncertain"
            printf 'Refusing: MySQL dump emitted WARNING or ERROR.\n' >&2
            return 1
        fi
        chmod 0600 "$dump_file" "$dump_log"
        chown ubuntu:ubuntu "$dump_file" "$dump_log"
    fi
}

production_green_wait_mysql() {
    for _ in {1..120}; do
        if mysqladmin --protocol=tcp --host=127.0.0.1 --port="$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT" -uroot ping >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    printf 'Refusing: production green MySQL did not become ready.\n' >&2
    return 1
}

production_green_initialize_mysql() {
    local data_dir="$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql"
    [[ -f "$data_dir/auto.cnf" ]] && return 0
    [[ -z "$(find "$data_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]] || {
        printf 'Refusing: production green MySQL data directory is not empty.\n' >&2
        return 1
    }
    mysqld --initialize-insecure --user=mysql --datadir="$data_dir" \
        --log-error="$data_dir/error.log" --lower-case-table-names=1
    chown -R ubuntu:mysql "$data_dir"
}

production_green_import_mysql() {
    local dump_file="$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/mysql.sql.gz"
    local db_password escaped_password
    db_password="$(production_green_docker_env_value bytedepth-mysql-1 MYSQL_ROOT_PASSWORD)"
    escaped_password="$(production_green_sql_escape "$db_password")"
    systemctl start "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE"
    production_green_wait_mysql
    gunzip -c "$dump_file" | mysql --protocol=tcp --host=127.0.0.1 \
        --port="$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT" -uroot
    mysql --protocol=tcp --host=127.0.0.1 --port="$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT" -uroot \
        -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$escaped_password'; FLUSH PRIVILEGES;"
    printf '[client]\nhost=127.0.0.1\nport=%s\nuser=root\npassword=%s\nprotocol=tcp\n' \
        "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT" "$db_password" > /etc/bytedepth/production-green-mysql.cnf
    chmod 0600 /etc/bytedepth/production-green-mysql.cnf
    chown ubuntu:ubuntu /etc/bytedepth/production-green-mysql.cnf
    mysql --defaults-extra-file=/etc/bytedepth/production-green-mysql.cnf \
        --batch --skip-column-names -e 'SELECT 1' >/dev/null
}

production_green_prepare_redis_snapshot() {
    local force="${1:-false}"
    local snapshot="$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/redis.rdb"
    local redis_password
    redis_password="$(production_green_docker_env_value bytedepth-redis-1 REDISCLI_AUTH)"
    if [[ "$force" == true || ! -f "$snapshot" ]]; then
        docker exec -e REDISCLI_AUTH="$redis_password" bytedepth-redis-1 \
            redis-cli --rdb /tmp/bytedepth-production-green.rdb >/dev/null
        docker cp bytedepth-redis-1:/tmp/bytedepth-production-green.rdb "$snapshot"
        docker exec bytedepth-redis-1 rm -f /tmp/bytedepth-production-green.rdb
        chmod 0600 "$snapshot"
        chown ubuntu:ubuntu "$snapshot"
    fi
    systemctl stop "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE" 2>/dev/null || true
    rm -f -- "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/dump.rdb"
    rm -rf -- "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/appendonlydir"
    install -o ubuntu -g redis -m 0640 "$snapshot" "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis/dump.rdb"
    systemctl start "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE"
    REDISCLI_AUTH="$redis_password" redis-cli -h 127.0.0.1 -p "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT" ping >/dev/null
}

production_green_ensure_meilisearch_binary() {
    if [[ ! -x /lib/ld-musl-x86_64.so.1 ]]; then
        command -v apt-get >/dev/null || { printf 'Refusing: musl loader is missing.\n' >&2; return 1; }
        apt-get update
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends musl
    fi
    install -d -o ubuntu -g ubuntu -m 0755 /usr/lib/x86_64-linux-musl
    if [[ ! -r /usr/lib/x86_64-linux-musl/libgcc_s.so.1 ]]; then
        docker exec bytedepth-meilisearch-1 /bin/sh -c 'cat /usr/lib/libgcc_s.so.1' \
            > /tmp/production-green-libgcc_s.so.1
        install -o ubuntu -g ubuntu -m 0644 /tmp/production-green-libgcc_s.so.1 \
            /usr/lib/x86_64-linux-musl/libgcc_s.so.1
        rm -f /tmp/production-green-libgcc_s.so.1
    fi
    if [[ ! -x /usr/local/bin/meilisearch ]]; then
        docker exec bytedepth-meilisearch-1 /bin/sh -c 'cat /bin/meilisearch' \
            > /tmp/production-green-meilisearch
        install -o ubuntu -g ubuntu -m 0755 /tmp/production-green-meilisearch /usr/local/bin/meilisearch
        rm -f /tmp/production-green-meilisearch
    fi
    /usr/local/bin/meilisearch --version | grep -Fq '1.7.'
}

production_green_prepare_meili_snapshot() {
    local force="${1:-false}"
    local snapshot="$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/meilisearch.snapshot"
    local task status source_snapshot
    if [[ "$force" == true || ! -f "$snapshot" ]]; then
        task="$(docker exec bytedepth-meilisearch-1 /bin/sh -c \
            'curl --fail --silent --show-error -X POST -H "Authorization: Bearer $MEILI_MASTER_KEY" http://127.0.0.1:7700/snapshots' | jq -er '.taskUid')"
        status=unknown
        for _ in {1..180}; do
            status="$(docker exec bytedepth-meilisearch-1 /bin/sh -c \
                "curl --fail --silent --show-error -H \"Authorization: Bearer \$MEILI_MASTER_KEY\" http://127.0.0.1:7700/tasks/$task" | jq -er '.status')"
            [[ "$status" == succeeded ]] && break
            [[ "$status" == failed ]] && return 1
            sleep 2
        done
        [[ "$status" == succeeded ]] || { printf 'Refusing: Meilisearch snapshot timed out.\n' >&2; return 1; }
        source_snapshot="$(find /data/meilisearch/snapshots -maxdepth 1 -type f -name '*.snapshot' -print | sort | tail -n 1)"
        [[ -f "$source_snapshot" ]] || { printf 'Refusing: Meilisearch snapshot file is missing.\n' >&2; return 1; }
        install -o ubuntu -g ubuntu -m 0600 "$source_snapshot" "$snapshot"
    fi
}

production_green_import_meili_snapshot() {
    local snapshot="$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/meilisearch.snapshot"
    local import_dir="$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch-import"
    local pid status
    systemctl stop "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE" 2>/dev/null || true
    rm -rf -- "$import_dir"
    install -d -o ubuntu -g ubuntu -m 0700 "$import_dir"
    (
        cd "$import_dir"
        exec /usr/local/bin/meilisearch --import-snapshot "$snapshot" \
            --db-path "$import_dir" --http-addr "127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT"
    ) > "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/meilisearch-import.log" 2>&1 &
    pid=$!
    for _ in {1..600}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            wait "$pid" || true
            printf 'Refusing: Meilisearch snapshot import exited early.\n' >&2
            return 1
        fi
        if curl --fail --silent --show-error \
            "http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT/health" >/dev/null \
            && curl --fail --silent --show-error \
            "http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT/indexes/posts" >/dev/null; then
            kill -TERM "$pid"
            set +e
            wait "$pid"
            status=$?
            set -e
            [[ "$status" -eq 0 || "$status" -eq 143 ]] || return 1
            break
        fi
        sleep 1
    done
    if kill -0 "$pid" 2>/dev/null; then
        kill -TERM "$pid" 2>/dev/null || true
        wait "$pid" || true
        printf 'Refusing: Meilisearch snapshot import exceeded 600 seconds.\n' >&2
        return 1
    fi
    rm -rf -- "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch"
    install -d -o ubuntu -g meilisearch -m 0770 "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch"
    cp -a -- "$import_dir"/. "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch/"
    rm -rf -- "$import_dir"
    printf '%s\n' 'env = "production"' > "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch/meilisearch.toml"
    chown -R ubuntu:meilisearch "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch"
    chmod -R g+rwX "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch"
    systemctl start "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE"
    curl --fail --silent --show-error --retry 30 --retry-delay 1 \
        "http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT/health" >/dev/null
}

production_green_copy_images() {
    rsync -a --delete /data/images/ "$BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT/"
    chown -R ubuntu:bytedepth "$BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT"
    chmod -R g+rwX "$BYTEDEPTH_PRODUCTION_GREEN_IMAGE_ROOT"
}

production_green_write_resource_manifest() {
    sha256sum \
        "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/mysql.sql.gz" \
        "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/redis.rdb" \
        "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/meilisearch.snapshot" \
        > "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/resource.manifest"
    chown ubuntu:ubuntu "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/resource.manifest"
    chmod 0600 "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/resource.manifest"
}

production_green_mark() {
    local marker="$1"
    touch "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/$marker"
    chown ubuntu:ubuntu "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/$marker"
}

production_green_prepare() {
    production_green_require_blue
    production_green_require_target
    [[ ! -e "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/prepared" ]] || return 0
    production_green_write_app_env
    "$SOURCE_ROOT/deploy/install-production-green-stack.sh"
    production_green_ensure_meilisearch_binary
    production_green_prepare_mysql_dump
    production_green_initialize_mysql
    production_green_import_mysql
    production_green_prepare_redis_snapshot
    production_green_prepare_meili_snapshot
    production_green_import_meili_snapshot
    production_green_copy_images
    production_green_write_resource_manifest
    production_green_mark prepared
    printf 'Prepared production green data and services; blue traffic is unchanged.\n'
}

production_green_final_sync() {
    production_green_require_blue
    production_green_require_target
    [[ -e "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/prepared" ]] || {
        printf 'Refusing: production green prepare must complete first.\n' >&2
        return 1
    }
    [[ "$(docker inspect -f '{{.State.Running}}' bytedepth-bytedepth-app-1)" == false ]] || {
        printf 'Refusing: blue bytedepth app must be stopped before final sync.\n' >&2
        return 1
    }
    production_green_mark syncing
    systemctl stop "$BYTEDEPTH_PRODUCTION_GREEN_EDGE_SERVICE" "$BYTEDEPTH_PRODUCTION_GREEN_APP_SERVICE" 2>/dev/null || true
    systemctl stop "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE" "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE" "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE" 2>/dev/null || true
    find "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/mysql" \
        "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/redis" \
        "$BYTEDEPTH_PRODUCTION_GREEN_ROOT/meilisearch" \
        -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
    production_green_prepare_mysql_dump true
    production_green_initialize_mysql
    production_green_import_mysql
    rm -f -- "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/redis.rdb" \
        "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/initial/meilisearch.snapshot"
    production_green_prepare_redis_snapshot true
    production_green_prepare_meili_snapshot true
    production_green_import_meili_snapshot
    production_green_copy_images
    rm -f -- "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/syncing"
    production_green_write_resource_manifest
    production_green_mark verified
    printf 'Final production green synchronization passed.\n'
}

production_green_verify() {
    production_green_require_target
    [[ -e "$BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT/verified" ]] || {
        printf 'Refusing: production green data has not been verified.\n' >&2
        return 1
    }
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_MYSQL_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_REDIS_SERVICE"
    systemctl is-active --quiet "$BYTEDEPTH_PRODUCTION_GREEN_MEILI_SERVICE"
    curl --fail --silent --show-error --retry 30 --retry-delay 1 \
        "http://127.0.0.1:$BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT/health" >/dev/null
}

production_green_mark_uncertain() {
    production_green_mark uncertain
}
