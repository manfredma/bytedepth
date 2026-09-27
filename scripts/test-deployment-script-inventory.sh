#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT

for retired in \
    "$ROOT/deploy/setup-shared-images-nfs.sh" \
    "$ROOT/deploy/bin/bytedepth-deploy-socket" \
    "$ROOT/deploy/bin/bytedepth-deploy-job" \
    "$ROOT/deploy/systemd/bytedepth-deploy.socket" \
    "$ROOT/deploy/systemd/bytedepth-deploy@.service" \
    "$ROOT/bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ops/UnixSocketOpsDeploymentAdapter.java" \
    "$ROOT/bytedepth-app/src/main/java/manfred/bytedepth/app/ops/OpsDeploymentPort.java"; do
    [[ ! -e "$retired" ]] || {
        printf 'Retired deployment artifact remains: %s\n' "${retired#"$ROOT"/}" >&2
        exit 1
    }
done

if rg -n 'install .*bytedepth-deploy-socket|install .*bytedepth-deploy-job|systemctl enable .*bytedepth-deploy\.socket|BYTEDEPTH_DEPLOY_SOCKET_PATH|ops:deploy:execute|/admin/ops/api/deployment|ops-deploy-release' \
    "$ROOT/deploy" "$ROOT/bytedepth-adapter/src/main" "$ROOT/bytedepth-app/src/main" \
    "$ROOT/bytedepth-infrastructure/src/main" "$ROOT/bytedepth-start/src/main/resources/application.yml" \
    "$ROOT/bytedepth-start/src/main/resources/templates/admin/ops/dashboard.html" \
    "$ROOT/docs/security/ops.md" "$ROOT/docs/architecture/routes.md" --glob '!**/target/**'; then
    printf 'Retired deployment control path is still wired into the active application.\n' >&2
    exit 1
fi

for installer in "$ROOT/deploy/install-host-service.sh" "$ROOT/deploy/install-production-stack.sh"; do
    rg -Fq 'systemctl disable --now bytedepth-deploy.socket' "$installer"
    rg -Fq '/run/bytedepth-deploy/deploy.sock' "$installer"
done

rg -Fq '网页运维页为只读页面，不发起 staging 或生产部署。' "$ROOT/docs/releases/README.md"
if rg -n '网页运维页只可展示或请求|网页运维部署按钮|重建完整 Compose 服务' \
    "$ROOT/docs/releases/README.md" "$ROOT/docs/security/ops.md" \
    "$ROOT/bytedepth-start/src/main/resources/templates/admin/ops/dashboard.html"; then
    printf 'Active documentation or UI still presents application-hosted deployment.\n' >&2
    exit 1
fi

migration="$ROOT/bytedepth-start/src/main/resources/db/migration/V26__remove_ops_deploy_permission.sql"
[[ -f "$migration" ]] || { printf 'Missing permission cleanup migration.\n' >&2; exit 1; }
rg -Fq "ops:deploy:execute" "$migration"
rg -Fq 'DELETE FROM `role_permission`' "$migration"
rg -Fq 'DELETE FROM `permission`' "$migration"

printf 'Deployment script inventory contract passed.\n'
