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
readonly MODE_CAPTURE="$FIXTURE_ROOT/bootstrap-mode"
readonly INSTALL_MARKER="$FIXTURE_ROOT/install-called"
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

# 构造一个 bare origin + 本地 checkout，用给定的 bootstrap 内容提交并推到 origin/main。
# $1 = bootstrap 脚本内容（heredoc 体）
# $2 = 可选，"real-deps" 时放置 fake install-host-service.sh 与 ctl.sh（供真实 bootstrap 调用）
setup_fixture() {
    local bootstrap_body="$1"
    local with_deps="${2:-}"

    git init --bare --initial-branch=main "$ORIGIN" >/dev/null
    git init --initial-branch=main "$CHECKOUT" >/dev/null
    git -C "$CHECKOUT" config user.name test
    git -C "$CHECKOUT" config user.email test@example.com
    git -C "$CHECKOUT" remote add origin "$ORIGIN"

    mkdir -p "$CHECKOUT/deploy"
    cp /workspace/deploy/deploy-staging.sh "$CHECKOUT/deploy/deploy-staging.sh"
    printf '%s\n' "$bootstrap_body" > "$CHECKOUT/deploy/bootstrap-ops-deploy.sh"
    chmod +x "$CHECKOUT/deploy/deploy-staging.sh" "$CHECKOUT/deploy/bootstrap-ops-deploy.sh"

    if [[ "$with_deps" == "real-deps" ]]; then
        # fake install-host-service.sh：记录被调用（真实版会装生产 Socket）。
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
    fi

    printf 'fixture\n' > "$CHECKOUT/fixture.txt"
    git -C "$CHECKOUT" add .
    git -C "$CHECKOUT" commit -m fixture >/dev/null
    git -C "$CHECKOUT" push -u origin main >/dev/null 2>&1
}

reset_state() {
    rm -rf "$ORIGIN" "$CHECKOUT" "$MODE_CAPTURE" "$INSTALL_MARKER" "$FAKE_BIN"
}

# --- 用例 1：staging mode 成功传播给 bootstrap ---

STAGING_AWARE_BOOTSTRAP='#!/usr/bin/env bash
set -Eeuo pipefail
printf "%s\n" "${BYTEDEPTH_DEPLOY_MODE:-unset}" > "'"$MODE_CAPTURE"'"
# 引用 BYTEDEPTH_DEPLOY_MODE，模拟 staging-aware 的真实 bootstrap。
if [[ "${BYTEDEPTH_DEPLOY_MODE:-}" != "staging" ]]; then
    : # 真实 bootstrap 在此调用 install-host-service.sh；测试用空操作代替
fi'

reset_state
install_fake_git
setup_fixture "$STAGING_AWARE_BOOTSTRAP"
write_conf staging

(cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/dev/null
actual_mode="$(cat "$MODE_CAPTURE")"
if [[ "$actual_mode" != "staging" ]]; then
    printf 'Expected bootstrap mode staging, got %s\n' "$actual_mode" >&2
    exit 1
fi

# --- 用例 2：配置为非 staging mode 时拒绝，且不执行 bootstrap ---

rm -f "$MODE_CAPTURE"
write_conf single-host

if (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/tmp/non-staging.out 2>&1; then
    printf 'Expected staging deployment to reject single-host mode\n' >&2
    exit 1
fi
if [[ -e "$MODE_CAPTURE" ]]; then
    printf 'Bootstrap ran after staging deployment rejected the configured mode\n' >&2
    exit 1
fi

# --- 用例 3（review A）：目标 commit 的 bootstrap 不引用 BYTEDEPTH_DEPLOY_MODE
# （模拟旧 Tag 的无条件 install-host-service.sh）时，必须拒绝且不执行该 bootstrap。 ---

reset_state
install_fake_git
# 旧式 bootstrap：无条件调用 install（用标记文件模拟），完全不引用 mode 变量。
OLD_BOOTSTRAP='#!/usr/bin/env bash
set -Eeuo pipefail
printf "%s\n" "install-called" > "'"$INSTALL_MARKER"'"
printf "%s\n" "ran" > "'"$MODE_CAPTURE"'"'
setup_fixture "$OLD_BOOTSTRAP"
write_conf staging

if (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/tmp/old-tag.out 2>&1; then
    printf 'Expected staging deployment to reject non-staging-aware bootstrap\n' >&2
    exit 1
fi
if [[ -e "$INSTALL_MARKER" ]]; then
    printf 'install-host-service ran for a non-staging-aware bootstrap\n' >&2
    exit 1
fi
if [[ -e "$MODE_CAPTURE" ]]; then
    printf 'Bootstrap executed despite being non-staging-aware\n' >&2
    exit 1
fi

# --- 用例 4（Minor 集成测试）：真实 bootstrap + fake installer 端到端验证。
# staging mode 经 deploy-staging.sh export 后，真实 bootstrap 必须跳过 install-host-service.sh
# （即不装生产 Socket）。这是「Socket 不会安装」事故路径的回归保护。 ---

reset_state
install_fake_git
setup_fixture "$(cat /workspace/deploy/bootstrap-ops-deploy.sh)" real-deps
write_conf staging

if ! (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/tmp/integration.out 2>&1; then
    printf 'staging deployment with real bootstrap failed:\n' >&2
    cat /tmp/integration.out >&2
    exit 1
fi
if [[ -e "$INSTALL_MARKER" ]]; then
    printf 'Real bootstrap called install-host-service.sh under staging mode\n' >&2
    exit 1
fi

# --- 用例 5（Minor 集成测试）：真实 bootstrap 在非 staging 环境下必须调用 install。
# 直接执行 bootstrap（绕过 deploy-staging.sh，后者会拒绝非 staging），验证 mode 分支对称性。 ---

rm -f "$INSTALL_MARKER"
# 非 staging 环境变量，直接跑真实 bootstrap + fake installer。
if ! (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" env BYTEDEPTH_DEPLOY_MODE=single-host \
        ./deploy/bootstrap-ops-deploy.sh) >/tmp/bootstrap-nostaging.out 2>&1; then
    printf 'Real bootstrap failed under single-host mode:\n' >&2
    cat /tmp/bootstrap-nostaging.out >&2
    exit 1
fi
if [[ ! -e "$INSTALL_MARKER" ]]; then
    printf 'Real bootstrap did not call install-host-service.sh under non-staging mode\n' >&2
    exit 1
fi

# --- 用例 6（分支 ref 接受）：staging 应接受 origin 上的非 main 分支用于预发验收。
# 场景：功能分支尚未合并 main，先部署到 staging 让所有者验收。
# 关键：该分支的 commit 必须不在 origin/main 上，否则测不到「非 main 分支」语义。 ---

reset_state
install_fake_git
setup_fixture "$(cat /workspace/deploy/bootstrap-ops-deploy.sh)" real-deps
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

# --- 用例 7（裸 SHA 拒绝）：任意裸 SHA 不在 origin 任何命名分支或 Tag 上时，必须拒绝。
# 安全底线：不能让任意 commit 以 root 构建+挂载。 ---

reset_state
install_fake_git
setup_fixture "$(cat /workspace/deploy/bootstrap-ops-deploy.sh)" real-deps
write_conf staging
# 制造一个不在任何分支/Tag 的裸 SHA：在 checkout 上新建一个 commit 但不推到 origin 任何 ref。
BARE_COMMIT=$(git -C "$CHECKOUT" commit-tree -m 'orphan' \
    "$(git -C "$CHECKOUT" rev-parse HEAD^{tree})")

if (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh "$BARE_COMMIT") >/tmp/bare.out 2>&1; then
    printf 'Expected staging deployment to reject a bare SHA\n' >&2
    exit 1
fi

printf 'deploy-staging regression tests passed\n'
