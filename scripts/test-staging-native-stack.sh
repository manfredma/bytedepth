#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly INSTALLER="$ROOT/deploy/install-staging-native-stack.sh"
readonly EXAMPLE="$ROOT/deploy/staging-native.conf.example"
readonly UNIT_DIR="$ROOT/deploy/systemd"
readonly TARGET_LIB="$ROOT/deploy/lib/staging-native-target.sh"
readonly PUBLIC_NGINX_TEMPLATE="$ROOT/deploy/nginx/staging-native-public.conf.template"

for path in "$INSTALLER" "$EXAMPLE" "$TARGET_LIB" "$PUBLIC_NGINX_TEMPLATE" \
    "$UNIT_DIR/bytedepth-staging-native-mysql.service.in" \
    "$UNIT_DIR/bytedepth-staging-native-redis.service.in" \
    "$UNIT_DIR/bytedepth-staging-native-meilisearch.service.in" \
    "$UNIT_DIR/bytedepth-staging-native-app.service.in" \
    "$UNIT_DIR/bytedepth-staging-native-edge.service.in" \
    "$UNIT_DIR/bytedepth-staging-native-test-slot.service.in"; do
    [[ -f "$path" ]] || { printf 'Missing staging-native stack asset: %s\n' "$path" >&2; exit 1; }
done

rg -q 'BYTEDEPTH_NATIVE_ROOT=/data/bytedepth-native-staging' "$EXAMPLE"
rg -q 'BYTEDEPTH_NATIVE_STACK_MODE=parallel' "$EXAMPLE"
rg -q 'BYTEDEPTH_NATIVE_MYSQL_PORT=13306' "$EXAMPLE"
rg -q 'BYTEDEPTH_NATIVE_REDIS_PORT=16379' "$EXAMPLE"
rg -q 'BYTEDEPTH_NATIVE_MEILI_PORT=17700' "$EXAMPLE"
rg -q 'BYTEDEPTH_NATIVE_APP_PORT=18080' "$EXAMPLE"
rg -q 'BYTEDEPTH_NATIVE_EDGE_PORT=18081' "$EXAMPLE"
rg -q 'proxy_pass http://127\.0\.0\.1:__NATIVE_EDGE_PORT__;' "$PUBLIC_NGINX_TEMPLATE"
rg -q 'ssl_certificate /etc/letsencrypt/live/__BYTEDEPTH_DOMAIN__/fullchain\.pem' "$PUBLIC_NGINX_TEMPLATE"
rg -q 'root /var/www/certbot' "$PUBLIC_NGINX_TEMPLATE"
rg -q 'install -d -o ubuntu -g ubuntu -m 0755 /var/www/certbot' "$INSTALLER"
rg -q 'PUBLIC_NGINX_CONF=/etc/nginx/conf\.d/bytedepth-staging\.conf' "$INSTALLER"
rg -q 'nginx -t' "$INSTALLER"
rg -q 'systemctl is-active --quiet nginx\.service' "$INSTALLER"
if rg -n 'systemctl (enable|restart).*nginx\.service|PUBLIC_NGINX_UNIT|nginx\.service\.d' \
    "$INSTALLER" >/dev/null; then
    printf 'Native staging must not take over or restart the shared nginx service.\n' >&2
    exit 1
fi
rg -Fq 'native_root" == /data/bytedepth-native-staging' "$INSTALLER"
rg -q 'BYTEDEPTH_NATIVE_STACK_MODE=parallel' "$INSTALLER"
rg -q 'BYTEDEPTH_STAGING_APP_SERVICE=bytedepth-staging-native-app.service' "$TARGET_LIB"
rg -q 'BYTEDEPTH_STAGING_TEST_IMAGE_ROOT="\$BYTEDEPTH_NATIVE_ROOT/images-test"' "$TARGET_LIB"
rg -q 'native staging ports must not collide' "$INSTALLER"
rg -q 'install -d -o ubuntu -g ubuntu -m 0700 "\$native_root/images-test"' "$INSTALLER"
rg -q 'chown ubuntu:bytedepth "\$native_root/images"' "$INSTALLER"
rg -q 'ensure_service_group_write_access' "$INSTALLER"
rg -q 'chmod -R g\+rwX' "$INSTALLER"
rg -q 'bytedepth-staging-native-mysql.service' "$INSTALLER"
rg -q 'EnvironmentFile=/etc/bytedepth/staging-native.env' "$UNIT_DIR/bytedepth-staging-native-app.service.in"
rg -q 'requirepass \$BYTEDEPTH_REDIS_PASSWORD' "$INSTALLER"
rg -q "'maxmemory 64mb'" "$INSTALLER"
rg -q "'maxmemory-policy noeviction'" "$INSTALLER"
rg -q 'local/usr.sbin.mysqld' "$INSTALLER"
rg -q 'apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld' "$INSTALLER"
rg -q 'capability chown' "$INSTALLER"
rg -q 'Type=simple' "$UNIT_DIR/bytedepth-staging-native-mysql.service.in"
rg -q '^MemoryMax=512M$' "$UNIT_DIR/bytedepth-staging-native-mysql.service.in"
rg -q '^Type=simple$' "$UNIT_DIR/bytedepth-staging-native-redis.service.in"
rg -q '^MemoryMax=128M$' "$UNIT_DIR/bytedepth-staging-native-redis.service.in"
rg -q 'ExecStart=/usr/sbin/mysqld.*__NATIVE_ROOT__/mysql.*__MYSQL_PORT__' "$UNIT_DIR/bytedepth-staging-native-mysql.service.in"
rg -q 'RuntimeDirectory=bytedepth-staging-native' "$UNIT_DIR/bytedepth-staging-native-mysql.service.in"
rg -q -- '--socket=/run/bytedepth-staging-native/mysql.sock' "$UNIT_DIR/bytedepth-staging-native-mysql.service.in"
rg -q -- '--log-error=__NATIVE_ROOT__/mysql/error.log' "$UNIT_DIR/bytedepth-staging-native-mysql.service.in"
rg -q 'ExecStart=/usr/bin/redis-server __NATIVE_ROOT__/redis/redis.conf' "$UNIT_DIR/bytedepth-staging-native-redis.service.in"
rg -q 'ExecStart=/usr/local/bin/meilisearch.*meilisearch.toml.*__NATIVE_ROOT__/meilisearch.*__MEILI_PORT__' "$UNIT_DIR/bytedepth-staging-native-meilisearch.service.in"
rg -q 'WorkingDirectory=__NATIVE_ROOT__/meilisearch' "$UNIT_DIR/bytedepth-staging-native-meilisearch.service.in"
rg -q '^MemoryMax=384M$' "$UNIT_DIR/bytedepth-staging-native-meilisearch.service.in"
rg -q 'ExecStart=/usr/sbin/nginx.*staging-native-nginx.conf' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q '^ExecReload=/bin/kill -HUP \$MAINPID$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q '^ExecStop=/bin/kill -QUIT \$MAINPID$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q 'nginx.pid' "$INSTALLER"
rg -q '^MemoryMax=64M$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q '^PIDFile=__NATIVE_ROOT__/edge/nginx.pid$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q '^ExecReload=/bin/kill -HUP \$MAINPID$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q '^ExecStop=/bin/kill -QUIT \$MAINPID$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
if rg -n '^Requires=bytedepth-staging-native-app\.service$' "$UNIT_DIR/bytedepth-staging-native-edge.service.in" >/dev/null; then
    printf 'Native staging edge must not stop with the app; E2E test-slot must be able to reuse the public edge.\n' >&2
    exit 1
fi
rg -q '^After=bytedepth-staging-native-app\.service ' "$UNIT_DIR/bytedepth-staging-native-edge.service.in"
rg -q 'Conflicts=bytedepth-staging-native-app.service' "$UNIT_DIR/bytedepth-staging-native-test-slot.service.in"
rg -q 'ReadWritePaths=__NATIVE_ROOT__/images-test' "$UNIT_DIR/bytedepth-staging-native-test-slot.service.in"
if rg -n '(/data/mysql|/data/redis|/data/meilisearch|:3306|:6379|:7700|:8080)' \
    "$UNIT_DIR/bytedepth-staging-native-"*.in >/dev/null; then
    printf 'Staging-native units must not reuse Docker data roots or default ports.\n' >&2
    exit 1
fi

printf 'Staging-native parallel stack contract passed.\n'
