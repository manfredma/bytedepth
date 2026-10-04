# 发布

ByteDepth 的发布生命周期完全由 release-platform 管理。本仓库不创建发布 Tag，不维护 staging/production 发布脚本，也不直接执行生产回滚。

统一流程：

`PR 合并 → 触发候选任务 → 完整 commit SHA → QUALITY → BUILD → staging 发布 → 页面验收 → 同一制品提升 production`

构建、发布、日志、重试、验证和回滚都必须在 release-platform 页面完成，并以页面中的候选、制品和任务记录为准。

触发候选任务只允许通过 release-platform 页面或授权平台连接器完成；仓库内的 Agent 只能准备 PR、完整 SHA 和触发参数，不能把本地脚本执行结果当成平台触发回执。

Agent 触发顺序固定为：创建候选 → 创建 `BUILD` flow instance → 启动 flow → 查询 artifact → 创建同 artifact 的 `RELEASE(staging)` flow → 启动 flow。API 细节和幂等要求见[平台发布说明](../engineering/release-platform-only.md)。

每个 flow 都必须持续读取 flow、stages、分页日志和 evidence；`202` 只表示请求进入队列。`FAILED` 仅在阶段允许重试时调用 retry，`SUCCEEDED` 必须核对完整 SHA 和不可变 artifact，不能凭页面按钮或本地构建结果判断发布完成。
