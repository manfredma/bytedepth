#!/usr/bin/env bash
set -Eeuo pipefail

# Local, read-only documentation check, called by run-local-quality.sh.
# It validates project Agent delivery rules without contacting CI/CD or hosts.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly GUIDE="$SOURCE_ROOT/docs/agent-guides/agent-responsibility-boundary.md"

require_text() {
  local text="$1"
  local file="$2"
  rg -Fq -- "$text" "$file" || {
    printf 'Missing Agent delivery rule in %s: %s\n' "$file" "$text" >&2
    exit 1
  }
}

require_text '默认推进到 staging 部署及验证完成、需要人工验收的位置' "$SOURCE_ROOT/AGENTS.md"
require_text '默认授权 Agent 在本任务范围内提交、推送分支、创建或更新 PR' "$GUIDE"
require_text '**默认（需要人工验收）**' "$GUIDE"
require_text '**明确免人工验收**' "$GUIDE"
require_text '用户对本任务明确说' "$GUIDE"
require_text '该授权不自动延续到其他任务' "$GUIDE"
require_text '授权依据必须记录为用户的免验收指令，不能伪称用户已查看或手工验收' "$GUIDE"
require_text '“继续”或没有回复不等于免人工验收授权' "$GUIDE"
require_text '生产验证成功的终态' "$GUIDE"
require_text '免人工验收只免除等待人工确认，不免除质量门禁' "$GUIDE"
require_text '**真实阻塞**' "$GUIDE"
require_text '**明确限制范围**' "$GUIDE"
require_text 'CI/CD 是构建、部署和验证的执行工具，不是 Agent 开发流程的授权来源' "$GUIDE"

# These checks preserve discoverability and required instructions, not Agent behavior.
require_text '#任务追踪与范围变更' "$SOURCE_ROOT/AGENTS.md"
require_text '#诊断与重试' "$SOURCE_ROOT/AGENTS.md"
require_text '单步且可当场验证的修改无需另建清单' "$GUIDE"
require_text '用户目标、可观察的验收条件、当前状态、证据位置' "$GUIDE"
require_text '上下文压缩后，先读取清单和最新用户指令' "$GUIDE"
require_text '追加本轮、替换旧要求或延后' "$GUIDE"
require_text '当前假设、支持证据、反证、已排除方向' "$SOURCE_ROOT/docs/engineering/gotchas.md"
require_text '没有新证据时停止重复执行同一方法' "$SOURCE_ROOT/docs/engineering/gotchas.md"
require_text '#任务追踪与范围变更' "$SOURCE_ROOT/docs/engineering/gotchas.md"
require_text '#诊断与重试' "$GUIDE"

# Keep the critical-path lesson in the existing task guide, not a second workflow.
require_text '## 主线优先与 backlog' "$GUIDE"
require_text '阻止哪一步、实际证据、最小解除动作' "$GUIDE"
require_text '未知项先安排最小验证，不直接宣称阻塞' "$GUIDE"
require_text '优先验证现有运行时和工具链' "$GUIDE"
require_text '主流程跑通后再处理 backlog' "$GUIDE"
require_text '已投入的时间、已批准的方案或局部测试通过都不能证明剩余工作仍属主线' "$GUIDE"
require_text '#主线优先与-backlog' "$SOURCE_ROOT/docs/README.md"

for file in \
  "$SOURCE_ROOT/docs/README.md" \
  "$SOURCE_ROOT/docs/engineering/git-workflow.md" \
  "$SOURCE_ROOT/docs/engineering/unified-release-pipeline.md" \
  "$SOURCE_ROOT/docs/engineering/release-platform-only.md" \
  "$SOURCE_ROOT/docs/releases/README.md" \
  "$SOURCE_ROOT/docs/architecture/decisions/0022-pr-candidate-staging-before-merge.md"; do
  require_text 'agent-responsibility-boundary.md' "$file"
done

# Preserve the canonical auth pointer; implementation belongs to the platform.
readonly RELEASE_GUIDE="$SOURCE_ROOT/docs/engineering/release-platform-only.md"
readonly AUTH_ANCHOR='#发布-agent-鉴权先读在线手册再验证'
require_text 'https://devops.bytedepth.cn/manual/agent/auth' "$RELEASE_GUIDE"
require_text '必须先完整读取' "$RELEASE_GUIDE"
require_text '完成条件是按手册取得当前调用进程的真实 API 验证结果' "$RELEASE_GUIDE"
require_text '均不足以证明系统缺少凭据' "$RELEASE_GUIDE"
require_text '不复制下载命令、脚本内容、Token 导入方式或鉴权请求头' "$RELEASE_GUIDE"
for file in \
  "$SOURCE_ROOT/AGENTS.md" \
  "$SOURCE_ROOT/docs/README.md" \
  "$GUIDE" \
  "$SOURCE_ROOT/docs/engineering/gotchas.md" \
  "$SOURCE_ROOT/docs/releases/README.md" \
  "$SOURCE_ROOT/deploy/README.md" \
  "$SOURCE_ROOT/docs/security/authentication.md"; do
  require_text "$AUTH_ANCHOR" "$file"
done
if rg -n 'launchctl (getenv|setenv) RELEASE_PLATFORM_AGENT_TOKEN|export RELEASE_PLATFORM_AGENT_TOKEN|/tmp/bytedepth-platform-request\.py|agent-auth/bootstrap-script' \
  "$SOURCE_ROOT/AGENTS.md" "$SOURCE_ROOT/docs/README.md" "$SOURCE_ROOT/deploy/README.md" \
  "$SOURCE_ROOT/docs/engineering" "$SOURCE_ROOT/docs/agent-guides" \
  "$SOURCE_ROOT/docs/releases/README.md" "$SOURCE_ROOT/docs/security" --glob '*.md'; then
  printf 'Platform auth commands belong to the canonical Agent auth manual.\n' >&2
  exit 1
fi
# User-facing authentication may document its own headers; only platform guides
# delegate their Bearer examples to the external manual.
if rg -n 'Authorization: Bearer' "$RELEASE_GUIDE" "$SOURCE_ROOT/docs/releases/README.md" "$SOURCE_ROOT/deploy/README.md"; then
  printf 'Platform auth headers belong to the canonical Agent auth manual.\n' >&2
  exit 1
fi

printf 'Agent delivery workflow documentation check passed.\n'
