#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/deploy-staging.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable staging deployment script.\n' >&2; exit 1; }
for contract in \
    'deployment-test.lock' \
    'staging-integration' \
    'staging-e2e' \
    'check-staging-changelog-change.sh' \
    'check-release-readiness.sh' \
    'build_release_artifact' \
    'scp' \
    'install_release_artifact' \
    'switch_current_release' \
    'BYTEDEPTH_STAGING_APP_SERVICE' \
    'BYTEDEPTH_STAGING_EDGE_SERVICE' \
    'ensure_edge_active_and_reload' \
    'restart_native_middlewares' \
    'wait_for_native_mysql' \
    'systemctl restart "$service"' \
    'systemctl start "$BYTEDEPTH_STAGING_EDGE_SERVICE"' \
    'load_staging_native_target' \
    'git fetch --force --no-recurse-submodules origin' \
    'git checkout --detach' \
    'verify_running_release' \
    'BYTEDEPTH_REMOTE_INSTALL=1' \
    'BYTEDEPTH_STAGING_SSH_KNOWN_HOSTS' \
    'staging-native-meilisearch.env' \
    'Refusing: staging native parallel configuration is incomplete.' \
    'staging-bytedepth.bytedepth.cn'; do
    rg -F -- "$contract" "$SCRIPT" >/dev/null || {
        printf 'Missing staging deployment contract: %s\n' "$contract" >&2
        exit 1
    }
done
if rg -n -F '/etc/bytedepth/application.env' "$SCRIPT" >/dev/null; then
    printf 'Staging deployment must not silently fall back to the legacy runtime when native parallel configuration is missing.\n' >&2
    exit 1
fi
rg -q 'check-staging-changelog-change.sh.*>&2' "$SCRIPT"
rg -q 'check-release-readiness.sh.*>&2' "$SCRIPT"
rg -q 'sudo -n grep -Fqx BYTEDEPTH_ENVIRONMENT=staging' "$SCRIPT"
rg -q 'sudo -n cat /etc/bytedepth/staging-native.conf >/dev/null 2>&1' "$SCRIPT"
rg -q "proxy_pass http://127\.0\.0\.1:18081;" "$SCRIPT"
rg -q "conf\.d/bytedepth-staging\.conf" "$SCRIPT"
rg -q "systemctl is-active --quiet nginx\.service" "$SCRIPT"
rg -q "systemctl reload nginx\.service" "$SCRIPT"
if rg -q "for legacy_unit .*nginx\.service" "$SCRIPT"; then
    printf 'Public native nginx must not be classified as a legacy runtime unit.\n' >&2
    exit 1
fi
rg -Fq 'print \$2' "$SCRIPT"
if rg -Fq 'print \\$2' "$SCRIPT"; then
    printf 'Staging SSH preflight must not over-escape awk positional parameters.\n' >&2
    exit 1
fi
if rg -n 'sudo -n (test -r|bash -c)|bash -c .*test -r' "$SCRIPT" >/dev/null; then
    printf 'Staging SSH preflight must not use nested shell readability checks.\n' >&2
    exit 1
fi
rg -q 'local ref="\$1" jar="\$2" manifest="\$3"' "$SCRIPT"
rg -q '"\$0" --lock-held "\$ref" "\$jar" "\$manifest"' "$SCRIPT"
if rg -n 'mysqldump|backup_database_preflight|/backups' "$SCRIPT" >/dev/null; then
    printf '普通 staging 部署不得执行全库数据库备份。\n' >&2
    exit 1
fi
if rg -n -i 'docker|compose|docker_build_and_rollout|bootstrap-staging-runtime|mvn ' "$SCRIPT" >/dev/null; then
    printf 'Staging deployment must build externally and install a native artifact.\n' >&2
    exit 1
fi
rg -Fq 'ls-remote --exit-code --heads' "$SCRIPT"
rg -Fq 'ls-remote --exit-code --tags' "$SCRIPT"
if rg -Fq 'ls-remote --heads --tags' "$SCRIPT"; then
    printf 'Staging deployment must check remote branches and tags separately.\n' >&2
    exit 1
fi

printf 'Staging deployment contract passed.\n'
