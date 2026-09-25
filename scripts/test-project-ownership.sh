#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly DEPLOY_DIR="$ROOT/deploy"

require_text() {
    local needle="$1"
    local file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing project ownership contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

# These scripts run with sudo on a server. Any persistent project file they
# create must explicitly be ubuntu-owned; service groups are allowed only as
# the group needed for the corresponding daemon to write its data.
readonly -a SERVER_SCRIPTS=(
    "$DEPLOY_DIR/install-host-service.sh"
    "$DEPLOY_DIR/install-staging-native-stack.sh"
    "$DEPLOY_DIR/deploy-staging.sh"
    "$DEPLOY_DIR/deploy-production.sh"
    "$DEPLOY_DIR/deploy-production-remote.sh"
    "$DEPLOY_DIR/migrate-staging-docker-to-native.sh"
    "$DEPLOY_DIR/provision-staging-test-slot.sh"
    "$DEPLOY_DIR/run-staging-integration-tests.sh"
    "$DEPLOY_DIR/run-staging-e2e-tests.sh"
    "$DEPLOY_DIR/setup-shared-images-nfs.sh"
    "$DEPLOY_DIR/sync-prod-to-staging.sh"
    "$DEPLOY_DIR/sync-staging-certificate-to-production.sh"
)

for file in "${SERVER_SCRIPTS[@]}"; do
    [[ -f "$file" ]] || { printf 'Missing server deployment script: %s\n' "$file" >&2; exit 1; }
    if rg -n '(^|[[:space:]])install( -d)? -m|(^|[[:space:]])install -m' "$file" >/dev/null; then
        printf 'Server deployment script has an install without explicit ownership: %s\n' "$file" >&2
        rg -n '(^|[[:space:]])install( -d)? -m|(^|[[:space:]])install -m' "$file" >&2
        exit 1
    fi
    if rg -n '(^|[[:space:]])chown( -R)? (mysql|redis|meilisearch|bytedepth):' "$file" >/dev/null; then
        printf 'Server deployment script gives a project path to a service account instead of ubuntu: %s\n' "$file" >&2
        rg -n '(^|[[:space:]])chown( -R)? (mysql|redis|meilisearch|bytedepth):' "$file" >&2
        exit 1
    fi
done

require_text 'ensure_project_tree_ownership' "$DEPLOY_DIR/install-host-service.sh"
require_text 'chown -R "$DEPLOY_USER:$DEPLOY_GROUP" "$SOURCE_ROOT"' "$DEPLOY_DIR/install-host-service.sh"
require_text 'install -o "$DEPLOY_USER" -g "$DEPLOY_GROUP" -m 0644' "$DEPLOY_DIR/install-host-service.sh"
require_text 'install -d -o ubuntu -g ubuntu -m 0700' "$DEPLOY_DIR/deploy-production-remote.sh"
require_text 'install -o ubuntu -g redis -m 0640' "$DEPLOY_DIR/sync-prod-to-staging.sh"
require_text 'chown -R ubuntu:bytedepth' "$DEPLOY_DIR/migrate-staging-docker-to-native.sh"
require_text 'staging_ensure_ubuntu_owner "$WORK_DIR"' "$DEPLOY_DIR/run-staging-integration-tests.sh"
require_text 'staging_ensure_ubuntu_owner "$WORK_DIR"' "$DEPLOY_DIR/run-staging-e2e-tests.sh"
require_text 'staging_ensure_ubuntu_owner "$maven_log"' "$DEPLOY_DIR/bootstrap-staging-runtime.sh"
require_text 'staging_ensure_ubuntu_owner "$E2E_LOG"' "$DEPLOY_DIR/run-staging-e2e-tests.sh"
require_text 'staging_ensure_ubuntu_owner "$MAVEN_LOG"' "$DEPLOY_DIR/run-staging-integration-tests.sh"
require_text '项目部署文件统一由 ubuntu 持有' "$ROOT/docs/engineering/gotchas.md"

if rg -n -i 'root-only|root-owned|root 管理|root 私有' \
    "$DEPLOY_DIR" "$ROOT/docs/engineering/gotchas.md" "$ROOT/docs/releases/README.md" \
    --glob '*.sh' --glob '*.service' --glob '*.in' --glob '*.md' >/dev/null; then
    printf 'Active deployment rules still contain obsolete root-only ownership language.\n' >&2
    rg -n -i 'root-only|root-owned|root 管理|root 私有' \
        "$DEPLOY_DIR" "$ROOT/docs/engineering/gotchas.md" "$ROOT/docs/releases/README.md" \
        --glob '*.sh' --glob '*.service' --glob '*.in' --glob '*.md' >&2
    exit 1
fi

printf 'Project ownership contract passed.\n'
