# ADR-0021: 本地质量门禁与 release-platform Host Agent 分工

- **状态**: Accepted
- **日期**: 2026-10-04
- **决策者**: 项目所有者与维护团队
- **取代**: [ADR-0003](0003-observable-isolated-delivery-pipeline.md)、[ADR-0006](0006-unified-release-pipeline.md)

## 上下文

bytedepth 曾通过 `.github/workflows/quality.yml` 在 PR 上重复执行本地质量脚本。当前发布平台已经具备按完整 commit SHA 执行 QUALITY、BUILD、staging 发布、验收和 production 提升的 Host Agent 流程。两套远程质量入口会重复消耗执行资源，也会让“PR 通过”与“候选可发布”出现两套事实来源。

## 决策

1. 本仓库的 AI Agent/开发者在 PR 前执行 `bash scripts/run-local-quality.sh`，完成 formatter、构建、单元测试、前端测试、lint、coverage 和脚本契约。
2. 删除 bytedepth 仓库的 GitHub Actions quality workflow 及其专用契约测试；GitHub 只承载代码、PR 和评审，不执行项目质量门禁。
3. PR 合并后，release-platform 使用同一完整 `main` SHA 执行 QUALITY/BUILD；平台页面和 Host Agent evidence 是候选质量与发布事实来源。
4. 本地质量结果不能替代 release-platform 对合并后候选 SHA 的重新执行；release-platform 结果也不改变本地提交前质量门禁的责任。

## 触发与监控

发布触发不是部署脚本调用，而是 release-platform 控制面流程：创建候选、创建并启动 `BUILD` flow instance，轮询 flow/stages/logs，确认不可变 artifact 后创建并启动同 artifact 的 `RELEASE(staging)` flow。每个 flow 必须保存 candidate/flow/request/idempotency 标识，并以服务端状态、日志、artifact 和 evidence 判断完成、等待、失败或可重试状态。

## 后果

- PR 不再等待重复的 GitHub quality workflow。
- 质量执行有唯一远程事实来源：release-platform Host Agent。
- 开发者仍获得本地快速反馈，且 formatter 在远程构建前已经完成。
- release-platform 暂时不可用时，PR 仍可完成本地质量，但不能声称候选已构建、staging 已验收或 production 可提升。

## 验证

- 不存在 `.github/workflows/quality.yml` 和 `scripts/test-github-quality-workflow.sh`。
- `scripts/check-staging-checklist.sh` 不再调用 GitHub Actions workflow 契约。
- `bash scripts/run-local-quality.sh` 与 `bash scripts/format-check.sh --all` 在本地通过。
- release-platform 侧以 flow/stage/artifact/evidence API 和页面记录验证合并 SHA 的执行状态。
