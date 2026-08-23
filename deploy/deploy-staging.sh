#!/usr/bin/env bash
# staging 部署：接受 origin 上的命名分支或 Tag（默认 main）。
# 在 124 上执行。与生产 deploy-release.sh 的区别：
# - 接受任意命名分支或 Tag（不限 SemVer Tag，不限 main），用于预发验收尚未合并 main 的功能分支
# - 不做重复部署校验（staging 可重复部署同一 ref）
# - 不做 POM-Tag 一致性校验
# - 安全限制：只接受 origin 上已命名的分支或 Tag，拒绝裸 SHA
#   （bootstrap-ops-deploy.sh 由 root 执行并构建带主机挂载的容器，
#    命名 ref 经 deploy key 推送，可追溯；裸 SHA 不可追溯，禁止）
# 用法：sudo ./deploy/deploy-staging.sh <ref>   # ref 默认 main，可为分支名或 Tag
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/deploy-staging.sh <ref>\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly GIT_REMOTE_URL=git@github.com:manfredma/bytedepth.git
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly HISTORY_FILE="$STATE_DIR/deploy-history"
readonly REF="${1:-main}"

git_cmd() { git -c safe.directory="$SOURCE_ROOT" "$@"; }
cd "$SOURCE_ROOT"

# 校验 origin
if [[ "$(git_cmd remote get-url origin)" != "$GIT_REMOTE_URL" ]]; then
    printf 'Refusing: origin must be %s\n' "$GIT_REMOTE_URL" >&2
    exit 1
fi

# deploy key（复用 /etc/bytedepth-deploy.conf 中的 GitHub deploy key）
CONFIG_FILE=/etc/bytedepth-deploy.conf
deploy_mode="$(awk -F= '$1=="BYTEDEPTH_DEPLOY_MODE"{value=$2} END{print value}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ "$deploy_mode" != "staging" ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging, got %s\n' "${deploy_mode:-unset}" >&2
    exit 1
fi
# bootstrap-ops-deploy.sh 依据环境变量决定是否安装生产部署 Socket。
export BYTEDEPTH_DEPLOY_MODE="$deploy_mode"
deploy_ssh_key="$(awk -F= '$1=="BYTEDEPTH_DEPLOY_SSH_KEY"{print $2}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ -z "$deploy_ssh_key" || ! -r "$deploy_ssh_key" ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_SSH_KEY missing or unreadable\n' >&2
    exit 1
fi

export GIT_SSH_COMMAND="ssh -i $deploy_ssh_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new"

# fetch ref，解析为完整 commit SHA
git_cmd fetch --force --no-recurse-submodules origin "$REF"
COMMIT="$(git_cmd rev-parse FETCH_HEAD^{commit})"

# 安全限制：只接受 origin 上已命名的分支或 Tag，拒绝裸 SHA。
# 原因：bootstrap-ops-deploy.sh 由 root 执行并构建带主机挂载的容器。
# 命名 ref（分支/Tag）经 deploy key 推送、可追溯；裸 SHA 不可追溯，禁止以 root 构建+挂载。
# ls-remote 对命名分支/Tag 返回 SHA，对裸 SHA 返回空。
if [[ -z "$(git_cmd ls-remote --heads --tags origin "$REF" 2>/dev/null)" ]]; then
    printf 'Refusing: %s is not a named branch or tag on origin\n' "$REF" >&2
    printf 'staging 只接受 origin 上已命名的分支或 Tag，拒绝裸 SHA\n' >&2
    exit 1
fi

git_cmd checkout --detach "$COMMIT"

# staging 兼容性校验（review A）：
# bootstrap-ops-deploy.sh 随目标 commit 一起 checkout，由 root 执行。
# 早期 Tag 的 bootstrap 无条件调用 install-host-service.sh（安装生产部署 Socket），
# 不认识 BYTEDEPTH_DEPLOY_MODE，部署到 staging 会违反「staging 不装 Socket」约定。
# 这里校验 checkout 出来的 bootstrap 是否引用了 staging mode 判定，
# 不认识的旧 Tag 在执行前直接拒绝，绝不触发其 installer。
readonly BOOTSTRAP="$SOURCE_ROOT/deploy/bootstrap-ops-deploy.sh"
if ! grep -q 'BYTEDEPTH_DEPLOY_MODE' "$BOOTSTRAP" 2>/dev/null; then
    printf 'Refusing: %s has a non-staging-aware bootstrap (no BYTEDEPTH_DEPLOY_MODE check)\n' "$REF" >&2
    printf '旧 Tag 的 bootstrap 会无条件安装生产部署 Socket，禁止部署到 staging\n' >&2
    exit 1
fi

./deploy/bootstrap-ops-deploy.sh

install -d -m 0700 "$STATE_DIR"
printf 'ref=%s\ncommit=%s\ndeployed_at=%s\n---\n' \
    "$REF" "$COMMIT" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s)\n' "$REF" "$COMMIT"
