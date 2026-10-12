# 发布

ByteDepth 的发布生命周期完全由 release-platform 管理。本仓库不创建发布 Tag，不维护 staging/production 发布脚本，也不直接执行生产回滚。

统一流程：

`PR head webhook → 候选 → QUALITY → BUILD → staging 发布 → 页面验收 → 合并同一 SHA → main SHA 校验 → 同一制品提升 production`

## 发布前准备版本号

候选创建前必须先在本仓库准备 changelog 版本。脚本只修改本地
`docs/releases/CHANGELOG.md`，不访问线上服务、不创建 Git Tag、不提交或推送代码。

```bash
# 普通功能发布：默认升级 minor，例如 v2.26.5 → v2.27.0
bash scripts/prepare-release.sh

# bug 修复：升级 patch，例如 v2.26.5 → v2.26.6
bash scripts/prepare-release.sh --patch

# 初始化版本或修正已有版本号
bash scripts/prepare-release.sh --version v2.27.0
```

脚本会把非空的 `## Unreleased` 固化为 `## [vX.Y.Z] - YYYY-MM-DD`，并在顶部保留新的空 `## Unreleased`。完成后将 changelog 变更提交到 PR；release-platform 使用当前 PR head 的完整 SHA 创建 candidate，先完成 staging 集成/E2E，再等待项目所有者验收。acceptance 的 `releaseTag` 必须与 changelog 正式版本一致；验收通过后由平台将同一 SHA fast-forward 合并到 main，再提升同一 artifact 到 production。版本号计算只读取本地 changelog，不访问线上服务。

本地 `check-release-readiness.sh` 以正式 section 标题作为版本事实源：候选冻结后 `Unreleased` 必须为空，版本必须高于基线，条目必须归入规定分类。无变更的已发布基线允许保留同一版本。平台管理的版本不要求历史 `**Tag**`、`**回滚基线**` 字段；Git Tag 和生产回滚状态以平台记录为准，不为通过本地门禁补造字段。若仍保留这些字段，Tag 必须与 section 版本一致，回滚版本必须低于候选且不高于基线，并且对应 Git Tag 必须是基线可达的 annotated tag；显式错误或无法解析的字段仍会被拒绝。契约测试通过实际版本准备脚本覆盖无历史字段的基线和冻结候选。

构建、发布、日志、重试、验证和回滚都必须在 release-platform 页面完成，并以页面中的候选、制品和任务记录为准。

触发候选任务只允许通过 release-platform 页面或授权平台连接器完成；仓库内的 Agent 只能准备 PR、完整 commit SHA 和触发参数，不能把本地脚本执行结果当成平台触发回执。

Agent 触发顺序固定为：准备 changelog 版本 → PR head 创建/复用候选 → `QUALITY`/`BUILD` → 创建同 artifact 的 `RELEASE(staging)` flow → staging 集成/E2E 与项目所有者验收 → 合并同一个 PR head SHA → 校验 `main` HEAD → 提升同一 artifact 到 production。API 细节和幂等要求见[平台发布说明](../engineering/release-platform-only.md)。

每个 flow 都必须持续读取 flow、stages、分页日志和 evidence；`202` 只表示请求进入队列。`FAILED` 仅在阶段允许重试时调用 retry，`SUCCEEDED` 必须核对完整 SHA 和不可变 artifact，不能凭页面按钮或本地构建结果判断发布完成。
