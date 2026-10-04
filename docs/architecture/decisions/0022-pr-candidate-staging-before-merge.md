# ADR-0022: PR 候选先 staging 验收再合并并提升同一制品

- **状态**: Accepted
- **日期**: 2026-10-04
- **决策者**: 项目所有者与维护团队
- **取代**: [ADR-0021](0021-local-quality-release-platform-agent.md) 中“合并后再构建验收”的时序

## 上下文

staging 是唯一的集成、E2E、部署和业务验收环境，production 只能提升已经验收的不可变制品。若先合并再验收，`main` 可能已经包含未经 staging 验证的代码，失败时还会产生回退和制品对应关系不清的问题。

## 决策

发布时固定使用以下顺序：

1. PR opened、reopened、synchronize 或 ready-for-review webhook 以 PR head 完整 SHA 创建或复用候选。
2. release-platform 对该候选执行 `QUALITY`、`BUILD`，并记录 candidate、flow、artifact 和 evidence 标识。
3. 使用同一不可变 artifact 发布 staging，Host Agent 执行集成测试、E2E、健康检查并生成 evidence；项目所有者在页面完成验收。
4. staging 验收通过后，合并仍指向同一完整 SHA 的 PR。
5. release-platform 校验 `main` HEAD 与已验收候选 SHA 完全一致；一致时提升同一 artifact 到 production。SHA 不一致时必须重新构建、重新 staging 和重新生成 evidence。

release-platform API 是标准控制面接口：候选使用 `POST /api/v1/projects/{projectId}/candidates`，构建和发布使用 `POST /api/v1/flow-instances` 加 `Idempotency-Key`，随后调用 `/start`；Agent 必须轮询 flow、stages、logs、artifacts 和 evidence，不以 HTTP `202` 作为成功依据。完整参数和监控规则见[平台发布说明](../../engineering/release-platform-only.md)。

## 后果

- 合并前即可发现 staging 集成和 E2E 问题，`main` 不会先进入未经验收的候选状态。
- 合并和 production 提升之间保持完整 SHA 与不可变 artifact 的一一对应。
- PR 标题不是制品身份；平台以 PR number 生成 `refs/pull/{number}/head`，以完整 commit SHA 锁定候选。
- 人工验收的 `releaseTag` 使用 SemVer；项目适配器版本与发布标签是不同字段，不能混填。
- release-platform 不可用或证据不完整时，PR 可以停留在待验收状态，但不能合并并声称可发布。

## 验证

- 项目知识库中的 Git 工作流、职责边界和发布说明均描述同一顺序。
- release-platform 页面/API 回执包含候选 SHA、flow 状态、artifact、staging evidence 和合并后的 `main` SHA 校验结果。
- `scripts/check-staging-checklist.sh` 和本地质量门禁在触发平台流程前通过；本地结果不替代 staging evidence。
