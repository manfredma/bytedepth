#!/usr/bin/env bash
# staging 部署：接受来自 main 分支或已打 Tag 的 ref。
# 在 124 上执行。与生产 deploy-release.sh 的区别：
# - 接受任意 ref（不限 SemVer Tag）
# - 不做重复部署校验（staging 可重复部署同一 ref）
# - 不做 POM-Tag 一致性校验
# - 安全限制：只接受 origin/main 或已 Tag 包含的 commit，不直接接受任意裸 SHA
# 用法：sudo ./deploy/deploy-staging.sh <ref>
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run with sudo: sudo ./deploy/deploy-staging.sh <ref>\n' >&2
    exit 1
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly GIT_REMOTE_URL=git@github.com:manfredma/bytedepth.git
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly HISTORY_FILE="$STATE_DIR/deploy-history"
readonly REF="${1:-}"

if [[ -z "$REF" ]]; then
    printf 'Usage: sudo ./deploy/deploy-staging.sh <ref>\n' >&2
    exit 1
fi

git_cmd() { git -c safe.directory="$SOURCE_ROOT" "$@"; }
cd "$SOURCE_ROOT"

# 校验 origin
if [[ "$(git_cmd remote get-url origin)" != "$GIT_REMOTE_URL" ]]; then
    printf 'Refusing: origin must be %s\n' "$GIT_REMOTE_URL" >&2
    exit 1
fi

# deploy key（复用 /etc/bytedepth-deploy.conf 中的 GitHub deploy key）
CONFIG_FILE=/etc/bytedepth-deploy.conf
deploy_ssh_key="$(awk -F= '$1=="BYTEDEPTH_DEPLOY_SSH_KEY"{print $2}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ -z "$deploy_ssh_key" || ! -r "$deploy_ssh_key" ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_SSH_KEY missing or unreadable\n' >&2
    exit 1
fi

export GIT_SSH_COMMAND="ssh -i $deploy_ssh_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new"

# fetch ref，解析为完整 commit SHA
git_cmd fetch --force --no-recurse-submodules origin "$REF"
COMMIT="$(git_cmd rev-parse FETCH_HEAD^{commit})"

# 安全限制：只接受 origin/main 或已 Tag 包含的 commit，不直接接受任意裸 SHA。
# 原因：bootstrap-ops-deploy.sh 由 root 执行并构建带主机挂载的容器，
# 任意 ref 等价于任意代码以 root 构建+挂载。
IS_MAIN=$(git_cmd branch -r --contains "$COMMIT" 2>/dev/null | grep -c "origin/main" || true)
IS_TAG=$(git_cmd tag --contains "$COMMIT" 2>/dev/null | head -1 || true)
if [[ "$IS_MAIN" -eq 0 && -z "$IS_TAG" ]]; then
    printf 'Refusing: %s is not on origin/main and not in any tag\n' "$REF" >&2
    printf 'staging 只接受来自 main 或已 Tag 的提交\n' >&2
    exit 1
fi

git_cmd checkout --detach "$COMMIT"
./deploy/bootstrap-ops-deploy.sh

install -d -m 0700 "$STATE_DIR"
printf 'ref=%s\ncommit=%s\ndeployed_at=%s\n---\n' \
    "$REF" "$COMMIT" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
printf 'Deployed %s (%s)\n' "$REF" "$COMMIT"
