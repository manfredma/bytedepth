#!/usr/bin/env bash
# staging 部署：接受 origin 上的命名候选分支或 Tag。
# 在 124 上执行。与生产 deploy-production.sh 的区别：
# - 接受任意命名分支或 Tag（不限 SemVer Tag，不限 main），用于预发验收尚未合并 main 的功能分支
# - 不做重复部署校验（staging 可重复部署同一 ref）
# - 不做 POM-Tag 一致性校验
# - 安全限制：只接受 origin 上已命名的分支或 Tag，拒绝裸 SHA
#   （bootstrap-ops-deploy.sh 由 root 执行并构建带主机挂载的容器，
#    命名 ref 经 deploy key 推送，可追溯；裸 SHA 不可追溯，禁止）
# 用法：./deploy/deploy-staging.sh <候选分支或Tag>   # 本机编排，远程 sudo 执行
set -Eeuo pipefail

if [[ $# -lt 1 || -z "${1:-}" ]]; then
    printf 'Usage: %s <candidate-branch-or-tag>\n' "$0" >&2
    exit 2
fi

if [[ "${EUID}" -ne 0 ]]; then
    STAGING_HOST="${BYTEDEPTH_STAGING_HOST:-124.221.143.25}"
    SSH_KEY="${BYTEDEPTH_SSH_KEY:-$HOME/.ssh/ubuntu_2.pem}"
    exec ssh -i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
        "ubuntu@$STAGING_HOST" \
        "cd /opt/bytedepth && sudo ./deploy/deploy-staging.sh $1"
fi

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly GIT_REMOTE_URL=git@github.com:manfredma/bytedepth.git
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly LOCK_FILE="$STATE_DIR/deployment-test.lock"
readonly HISTORY_FILE="$STATE_DIR/deploy-history"
readonly TIMING_DIR="$STATE_DIR/timing"
readonly EXPECTED_STAGING_DOMAIN=staging-bytedepth.bytedepth.cn
readonly EXPECTED_STAGING_SITE_URL=https://bytedepth.cn

# A deployment changes both the checkout and the running app.  Keep that
# transition indivisible with respect to staging test runners, otherwise a
# runner can test one revision and write evidence for another.
if [[ "${1:-}" != '--lock-held' ]]; then
    install -d -o root -g root -m 0700 "$STATE_DIR"
    exec flock -x "$LOCK_FILE" "$0" --lock-held "$@"
fi
shift

readonly REF="$1"

git_cmd() { git -c safe.directory="$SOURCE_ROOT" "$@"; }

invalidate_test_evidence() {
    rm -f "$STATE_DIR/test-history/staging-integration" \
        "$STATE_DIR/test-history/staging-e2e"
}

require_staging_host_configuration() {
    local configured_domain configured_site_url certificate_dir certificate_san_names

    configured_domain="$(awk -F= '$1 == "BYTEDEPTH_DOMAIN" {value = substr($0, index($0, "=") + 1)} END {print value}' .env 2>/dev/null || true)"
    if [[ "$configured_domain" != "$EXPECTED_STAGING_DOMAIN" ]]; then
        printf 'Refusing: staging BYTEDEPTH_DOMAIN must be %s, got %s\n' \
            "$EXPECTED_STAGING_DOMAIN" "${configured_domain:-unset}" >&2
        exit 1
    fi

    configured_site_url="$(awk -F= '$1 == "BYTEDEPTH_SITE_URL" {value = substr($0, index($0, "=") + 1)} END {print value}' .env 2>/dev/null || true)"
    if [[ -n "$configured_site_url" && "$configured_site_url" != "$EXPECTED_STAGING_SITE_URL" ]]; then
        printf 'Refusing: staging BYTEDEPTH_SITE_URL must remain %s, got %s\n' \
            "$EXPECTED_STAGING_SITE_URL" "$configured_site_url" >&2
        exit 1
    fi

    certificate_dir="/etc/letsencrypt/live/$EXPECTED_STAGING_DOMAIN"
    if [[ ! -r "$certificate_dir/fullchain.pem" || ! -r "$certificate_dir/privkey.pem" ]]; then
        printf 'Refusing: staging TLS certificate is missing for %s\n' "$EXPECTED_STAGING_DOMAIN" >&2
        exit 1
    fi
    certificate_san_names="$(openssl x509 -in "$certificate_dir/fullchain.pem" -noout -ext subjectAltName 2>/dev/null || true)"
    if ! printf '%s\n' "$certificate_san_names" \
        | tr ',' '\n' \
        | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
        | grep -Fx "DNS:$EXPECTED_STAGING_DOMAIN" >/dev/null; then
        printf 'Refusing: staging TLS certificate does not cover %s\n' "$EXPECTED_STAGING_DOMAIN" >&2
        exit 1
    fi
}

bound_build_cache() {
    # A staging node has a finite system disk. Keep recent layers for build
    # speed, but never let accumulated BuildKit cache exhaust the node.
    docker builder prune --all --force --max-used-space 5GB
}

cd "$SOURCE_ROOT"
deployment_started_at="$(date -u +%s%3N)"

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
require_staging_host_configuration
deploy_ssh_key="$(awk -F= '$1=="BYTEDEPTH_DEPLOY_SSH_KEY"{print $2}' "$CONFIG_FILE" 2>/dev/null || true)"
if [[ -z "$deploy_ssh_key" || ! -r "$deploy_ssh_key" ]]; then
    printf 'Refusing: BYTEDEPTH_DEPLOY_SSH_KEY missing or unreadable\n' >&2
    exit 1
fi

export GIT_SSH_COMMAND="ssh -i $deploy_ssh_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new"

# fetch ref，解析为完整 commit SHA
source_fetch_started_at="$(date -u +%s%3N)"
git_cmd fetch --force --no-recurse-submodules origin "$REF" main
COMMIT="$(git_cmd rev-parse FETCH_HEAD^{commit})"
export BYTEDEPTH_COMMIT_ID="$COMMIT"
export BYTEDEPTH_BUILT_AT="$(date -u +%FT%TZ)"

# 安全限制：只接受 origin 上已命名的分支或 Tag，拒绝裸 SHA。
# 原因：bootstrap-ops-deploy.sh 由 root 执行并构建带主机挂载的容器。
# 命名 ref（分支/Tag）经 deploy key 推送、可追溯；裸 SHA 不可追溯，禁止以 root 构建+挂载。
# ls-remote 对命名分支/Tag 返回 SHA，对裸 SHA 返回空。
if [[ -z "$(git_cmd ls-remote --heads --tags origin "$REF" 2>/dev/null)" ]]; then
    printf 'Refusing: %s is not a named branch or tag on origin\n' "$REF" >&2
    printf 'staging 只接受 origin 上已命名的分支或 Tag，拒绝裸 SHA\n' >&2
    exit 1
fi

source <(git show "$COMMIT:deploy/lib/timing.sh")
readonly TIMING_FILE="$TIMING_DIR/$COMMIT"
initialize_timing_file "$TIMING_FILE" "$COMMIT"
record_timing_phase "$TIMING_FILE" source_fetch passed "$source_fetch_started_at" "$(timing_now_epoch_ms)"

if ! record_timed_phase "$TIMING_FILE" source_checkout git_cmd checkout --detach "$COMMIT"; then
    record_timing_phase "$TIMING_FILE" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"
    exit 1
fi

bash scripts/check-staging-changelog-change.sh --target "$COMMIT" --base origin/main
bash scripts/check-release-readiness.sh --target "$COMMIT" --base origin/main --mode candidate

# Any prior result describes the previously deployed application, never this
# deployment.  Do this only after candidate readiness passes, while holding
# the same lock as both test runners.
invalidate_test_evidence

source "$SOURCE_ROOT/deploy/lib/staging-runtime.sh"
run_runtime_preflight() {
    # Keep dependency inputs and the deployed checkout in sync before rollout;
    # test runners must never discover a missing manifest after deployment.
    ./deploy/bootstrap-staging-runtime.sh --lock-held --ensure
    require_staging_runtime_prerequisites
}
if ! record_timed_phase "$TIMING_FILE" runtime_preflight run_runtime_preflight; then
    record_timing_phase "$TIMING_FILE" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"
    exit 1
fi

run_rollout() {
    ./deploy/bootstrap-ops-deploy.sh
    bound_build_cache
}
if ! record_timed_phase "$TIMING_FILE" docker_build_and_rollout run_rollout; then
    record_timing_phase "$TIMING_FILE" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"
    exit 1
fi

install -d -m 0700 "$STATE_DIR"
printf 'ref=%s\ncommit=%s\ndeployed_at=%s\n---\n' \
    "$REF" "$COMMIT" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
record_timing_phase "$TIMING_FILE" deployment_total passed "$deployment_started_at" "$(timing_now_epoch_ms)"
printf 'Deployed %s (%s)\n' "$REF" "$COMMIT"
