#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT DEPLOY_DIR="$ROOT/deploy"
readonly -a SERVER_SCRIPTS=(
    "$DEPLOY_DIR/install-host-service.sh"
    "$DEPLOY_DIR/install-staging-native-stack.sh"
    "$DEPLOY_DIR/install-production-stack.sh"
    "$DEPLOY_DIR/deploy-staging.sh"
    "$DEPLOY_DIR/deploy-production.sh"
    "$DEPLOY_DIR/deploy-production-remote.sh"
    "$DEPLOY_DIR/provision-staging-test-slot.sh"
    "$DEPLOY_DIR/run-staging-integration-tests.sh"
    "$DEPLOY_DIR/run-staging-e2e-tests.sh"
    "$DEPLOY_DIR/setup-shared-images-nfs.sh"
    "$DEPLOY_DIR/sync-prod-to-staging.sh"
    "$DEPLOY_DIR/sync-staging-certificate-to-production.sh"
)
for file in "${SERVER_SCRIPTS[@]}"; do
    [[ -f "$file" ]] || { printf 'Missing deployment script: %s\n' "$file" >&2; exit 1; }
    if rg -n '(^|[[:space:]])(chown( -R)? (root|0):|install .* -o root|install .* -g root)' "$file"; then
        printf 'Project deployment resources must not be assigned to the superuser: %s\n' "$file" >&2
        exit 1
    fi
    if rg -n '(^|[[:space:]])install( -d)? -m|(^|[[:space:]])install -m' "$file"; then
        printf 'Persistent install calls must explicitly set owner and group: %s\n' "$file" >&2
        exit 1
    fi
done
rg -F 'readonly DEPLOY_USER=ubuntu' "$DEPLOY_DIR/install-host-service.sh" >/dev/null
rg -F 'ensure_project_tree_ownership' "$DEPLOY_DIR/install-host-service.sh" >/dev/null
rg -F 'chown -R "$DEPLOY_USER:$DEPLOY_GROUP" "$SOURCE_ROOT"' "$DEPLOY_DIR/install-host-service.sh" >/dev/null
rg -F 'chown -h ubuntu:ubuntu "$current_link"' "$DEPLOY_DIR/lib/artifact.sh" >/dev/null
rg -F 'install -d -o ubuntu -g ubuntu -m 0755 "$release_dir"' "$DEPLOY_DIR/lib/artifact.sh" >/dev/null
rg -F 'install -o ubuntu -g ubuntu -m 0644 "$jar" "$release_dir/app.jar"' "$DEPLOY_DIR/lib/artifact.sh" >/dev/null
rg -F 'install -o ubuntu -g ubuntu -m 0600 "$manifest" "$release_dir/artifact.manifest"' "$DEPLOY_DIR/lib/artifact.sh" >/dev/null
rg -F 'chown -R ubuntu:ubuntu /var/lib/bytedepth-production' "$DEPLOY_DIR/deploy-production.sh" >/dev/null
rg -F 'staging_ensure_ubuntu_owner "$WORK_DIR"' "$DEPLOY_DIR/run-staging-integration-tests.sh" >/dev/null
rg -F 'staging_ensure_ubuntu_owner "$WORK_DIR"' "$DEPLOY_DIR/run-staging-e2e-tests.sh" >/dev/null
rg -F 'staging_ensure_ubuntu_owner "$MAVEN_LOG"' "$DEPLOY_DIR/run-staging-integration-tests.sh" >/dev/null
rg -F 'staging_ensure_ubuntu_owner "$E2E_LOG"' "$DEPLOY_DIR/run-staging-e2e-tests.sh" >/dev/null
rg -F '项目部署文件统一由 ubuntu 持有' "$ROOT/docs/engineering/gotchas.md" >/dev/null
printf 'Project ownership contract passed.\n'
