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

for file in \
  "$SOURCE_ROOT/docs/README.md" \
  "$SOURCE_ROOT/docs/engineering/git-workflow.md" \
  "$SOURCE_ROOT/docs/engineering/unified-release-pipeline.md" \
  "$SOURCE_ROOT/docs/engineering/release-platform-only.md" \
  "$SOURCE_ROOT/docs/releases/README.md" \
  "$SOURCE_ROOT/docs/architecture/decisions/0022-pr-candidate-staging-before-merge.md"; do
  require_text 'agent-responsibility-boundary.md' "$file"
done

printf 'Agent delivery workflow documentation check passed.\n'
