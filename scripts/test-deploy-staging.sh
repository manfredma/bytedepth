#!/usr/bin/env bash
set -Eeuo pipefail

# deploy-staging.sh 行为回归测试。
#
# 在隔离容器内运行（maven:3.9-eclipse-temurin-17），原因有二：
#   1. 需要写 /etc/bytedepth-deploy.conf、/tmp 部署 key，宿主执行会破坏真实部署状态；
#   2. --container 模式用于确认当前确实运行在容器内，宿主直接执行被拒绝（review B）。
#
# 用法：
#   bash scripts/test-deploy-staging.sh            # 自动拉起容器（仅限已装 Docker 的开发机）
#   bash scripts/test-deploy-staging.sh --container # 容器内执行（CI/被上面的调用拉起）

# 宿主保护（review B）：--container 只代表「调用方声称在容器内」，
# 必须进一步确认运行环境确实是容器，否则 sudo 在宿主直接跑会覆盖 /etc/bytedepth-deploy.conf。
is_container_env() {
    [[ -f /.dockerenv ]] \
        || grep -qaE '(docker|containerd|lxc|kubepods)' /proc/1/cgroup 2>/dev/null
}

if [[ "${1:-}" != "--container" ]]; then
    exec docker run --rm \
        -v "$(cd "$(dirname "$0")/.." && pwd):/workspace:ro" \
        maven:3.9-eclipse-temurin-17 \
        bash /workspace/scripts/test-deploy-staging.sh --container
fi

# 到这里说明 $1 == --container。但调用方可能撒谎（sudo 在宿主直接传 --container），
# 因此再次校验运行环境。非容器环境立即拒绝，不触碰 /etc。
if ! is_container_env; then
    printf 'Refusing: --container requested but not running inside a container.\n' >&2
    printf 'This test writes /etc/bytedepth-deploy.conf; run it via Docker or inside a container.\n' >&2
    exit 1
fi

readonly FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

readonly ORIGIN="$FIXTURE_ROOT/origin.git"
readonly CHECKOUT="$FIXTURE_ROOT/checkout"
readonly INSTALL_MARKER="$FIXTURE_ROOT/install-called"
readonly BOOTSTRAP_RAN="$FIXTURE_ROOT/bootstrap-ran"
readonly FAKE_BIN="$FIXTURE_ROOT/bin"

DEPLOY_KEY=/tmp/bytedepth-test-deploy-key
touch "$DEPLOY_KEY"
chmod 0600 "$DEPLOY_KEY"

write_conf() {
    local mode="$1"
    printf 'BYTEDEPTH_DEPLOY_MODE=%s\nBYTEDEPTH_DEPLOY_SSH_KEY=%s\n' "$mode" "$DEPLOY_KEY" \
        > /etc/bytedepth-deploy.conf
}

# 构造 fake git：仅拦截 remote get-url origin，其余透传 /usr/bin/git。
install_fake_git() {
    mkdir -p "$FAKE_BIN"
    cat > "$FAKE_BIN/git" <<'SCRIPT'
#!/usr/bin/env bash
if [[ "$*" == *" remote get-url origin" ]]; then
    printf 'git@github.com:manfredma/bytedepth.git\n'
    exit 0
fi
exec /usr/bin/git "$@"
SCRIPT
    chmod +x "$FAKE_BIN/git"
}

# 构造一个 bare origin + 本地 checkout，用真实 bootstrap 提交并推到 origin/main。
# "real-deps" 时放置 fake install-host-service.sh 与 ctl.sh（供真实 bootstrap 调用）。
setup_fixture() {
    git init --bare --initial-branch=main "$ORIGIN" >/dev/null
    git init --initial-branch=main "$CHECKOUT" >/dev/null
    git -C "$CHECKOUT" config user.name test
    git -C "$CHECKOUT" config user.email test@example.com
    git -C "$CHECKOUT" remote add origin "$ORIGIN"

    mkdir -p "$CHECKOUT/deploy"
    cp /workspace/deploy/deploy-staging.sh "$CHECKOUT/deploy/deploy-staging.sh"
    cp /workspace/deploy/bootstrap-ops-deploy.sh "$CHECKOUT/deploy/bootstrap-ops-deploy.sh"
    chmod +x "$CHECKOUT/deploy/deploy-staging.sh" "$CHECKOUT/deploy/bootstrap-ops-deploy.sh"

    # fake install-host-service.sh：记录被调用（真实版装部署 Socket）。
    cat > "$CHECKOUT/deploy/install-host-service.sh" <<SCRIPT
#!/usr/bin/env bash
printf '%s\n' 'install-called' > "$INSTALL_MARKER"
SCRIPT
    # fake ctl.sh：空操作成功，避免真实 compose 依赖。
    cat > "$CHECKOUT/deploy/ctl.sh" <<'SCRIPT'
#!/usr/bin/env bash
exit 0
SCRIPT
    chmod +x "$CHECKOUT/deploy/install-host-service.sh" "$CHECKOUT/deploy/ctl.sh"

    printf 'fixture\n' > "$CHECKOUT/fixture.txt"
    git -C "$CHECKOUT" add .
    git -C "$CHECKOUT" commit -m fixture >/dev/null
    git -C "$CHECKOUT" push -u origin main >/dev/null 2>&1
}

reset_state() {
    rm -rf "$ORIGIN" "$CHECKOUT" "$INSTALL_MARKER" "$BOOTSTRAP_RAN" "$FAKE_BIN"
}

# --- 用例 1：配置为非 staging mode 时 deploy-staging.sh 拒绝，且不执行 bootstrap ---

reset_state
install_fake_git
setup_fixture
write_conf single-host

if (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/tmp/non-staging.out 2>&1; then
    printf 'Expected staging deployment to reject single-host mode\n' >&2
    exit 1
fi
if [[ -e "$INSTALL_MARKER" ]]; then
    printf 'install-host-service ran after staging deployment rejected the configured mode\n' >&2
    exit 1
fi

# --- 用例 2：staging mode 下 bootstrap 无条件调用 install-host-service.sh（装 Socket）。
# Socket 是远程触发部署的通道，所有模式（含 staging）都安装，staging 用于测试该通道。 ---

reset_state
install_fake_git
setup_fixture
write_conf staging

if ! (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/tmp/staging.out 2>&1; then
    printf 'staging deployment with real bootstrap failed:\n' >&2
    cat /tmp/staging.out >&2
    exit 1
fi
if [[ ! -e "$INSTALL_MARKER" ]]; then
    printf 'Real bootstrap did not call install-host-service.sh under staging mode\n' >&2
    printf 'Socket 应在所有模式安装（含 staging，用于测试远程部署通道）\n' >&2
    exit 1
fi

# --- 用例 3：bootstrap 是 mode-agnostic——不读 BYTEDEPTH_DEPLOY_MODE 环境变量。
# 直接执行 bootstrap（绕过 deploy-staging.sh），无论 mode 环境变量如何都调 install。 ---

reset_state
install_fake_git
setup_fixture

# 即使设了 staging mode 环境变量，bootstrap 仍应调 install（不再有 mode 跳过逻辑）。
if ! (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" env BYTEDEPTH_DEPLOY_MODE=staging \
        ./deploy/bootstrap-ops-deploy.sh) >/tmp/bootstrap-staging-env.out 2>&1; then
    printf 'Real bootstrap failed with BYTEDEPTH_DEPLOY_MODE=staging env:\n' >&2
    cat /tmp/bootstrap-staging-env.out >&2
    exit 1
fi
if [[ ! -e "$INSTALL_MARKER" ]]; then
    printf 'bootstrap skipped install when BYTEDEPTH_DEPLOY_MODE=staging was set\n' >&2
    printf 'bootstrap 应 mode-agnostic，不再因 staging 跳过 install\n' >&2
    exit 1
fi

# --- 用例 4：分支 ref 接受——staging 接受 origin 上的非 main 分支用于预发验收。
# 场景：功能分支尚未合并 main，先部署到 staging 让所有者验收。
# 关键：该分支的 commit 必须不在 origin/main 上，否则测不到「非 main 分支」语义。 ---

reset_state
install_fake_git
setup_fixture
# 在 main 之上新增一个 commit，只推到 feat 分支，不推回 main。
git -C "$CHECKOUT" commit --allow-empty -m 'feature commit not on main' >/dev/null
git -C "$CHECKOUT" branch feat/verify-staging
git -C "$CHECKOUT" push origin feat/verify-staging >/dev/null 2>&1
write_conf staging

if ! (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh feat/verify-staging) >/tmp/branch.out 2>&1; then
    printf 'staging deployment of a non-main branch failed:\n' >&2
    cat /tmp/branch.out >&2
    exit 1
fi

# --- 用例 5：裸 SHA 拒绝——任意裸 SHA 不在 origin 任何命名分支或 Tag 上时，必须拒绝。
# 安全底线：不能让任意 commit 以 root 构建+挂载。 ---

reset_state
install_fake_git
setup_fixture
write_conf staging
# 制造一个不在任何分支/Tag 的裸 SHA：在 checkout 上新建一个 commit 但不推到 origin 任何 ref。
BARE_COMMIT=$(git -C "$CHECKOUT" commit-tree -m 'orphan' \
    "$(git -C "$CHECKOUT" rev-parse HEAD^{tree})")

if (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh "$BARE_COMMIT") >/tmp/bare.out 2>&1; then
    printf 'Expected staging deployment to reject a bare SHA\n' >&2
    exit 1
fi

printf 'deploy-staging regression tests passed\n'
