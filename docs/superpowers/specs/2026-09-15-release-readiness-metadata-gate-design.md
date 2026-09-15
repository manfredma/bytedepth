# 发布就绪变更记录门禁设计

**关联 ADR：** [ADR-0007](../../architecture/decisions/0007-release-readiness-metadata-gate.md)

## 目标

确保任何包含用户可见、运行时、部署或配置变化的候选，在首次 staging 部署前已经拥有明确且非空的 `CHANGELOG.md` `Unreleased` 条目；任何遗漏都必须在本地质量、CI 或 staging 入口中 fail-closed，而不是等到创建正式 Tag 才发现。

## 唯一规范

`docs/releases/CHANGELOG.md` 同时包含两类内容：

- `## Unreleased`：开发和 PR 阶段的变更集合，不对应 Tag；任何用户可见、运行时、部署或配置变更在首次 staging 前必须写入这里。
- `## [vX.Y.Z] - YYYY-MM-DD`：正式发布条目，必须与一个不可变 annotated Tag 一一对应。

有效的 `Unreleased` 必须包含至少一个 Keep a Changelog 分类标题（`Added`、`Changed`、`Deprecated`、`Removed`、`Fixed`、`Security` 或 `Compatibility`）和该分类下至少一个非空列表项。只含标题、空分类或只有 HTML 注释均视为无效。

## 变更范围判定

检查脚本比较候选提交与 `origin/main` 的共同祖先；若变更文件包含应用 Java、模板、前端资源、数据库迁移、部署脚本、Compose、Dockerfile、配置或依赖清单，则要求有效 `Unreleased`。只修改 `docs/`（但不包含 `docs/releases/CHANGELOG.md`）或其他纯说明文件时可豁免。无法判断或无法取得基线时失败，不默认豁免。

## 强制入口

共享检查脚本只负责判定变更范围和 Changelog 结构，不访问外部服务，也不写文件。以下入口必须调用它：

1. `scripts/run-local-quality.sh`：在测试前失败，避免不完整候选被提交或推送。
2. `.github/workflows/quality.yml`：通过本地质量入口继承同一检查，PR 不能绕过。
3. `deploy/deploy-staging.sh`：解析并验证候选 SHA 后、构建前执行；失败时不得改变运行中的 staging 或写入部署/evidence 状态。
4. `scripts/merge-main-after-quality.sh`：合并前对远程候选再次检查，并要求候选仍是可 fast-forward 的基线。
5. `scripts/prepare-release.sh`：保留本检查作为防御性复核，同时检查正式版本标题、main SHA evidence 和 Tag 唯一性。

所有入口都必须在使用的同一候选 SHA 上检查；任何提交变化都使之前的 staging evidence 失效，必须重新部署和测试。

## 错误处理与安全边界

- 缺少 `CHANGELOG.md`、缺少 `Unreleased`、结构无效、变更范围无法计算或基线不可用：返回非零并输出可操作的修复提示。
- 脚本不得自动替用户编辑 Changelog，避免生成无意义或错误的发布说明。
- 检查不读取 `.env`、凭据或外部服务；staging 与生产权限边界不变。
- 正式发布阶段将 `Unreleased` 转为版本标题属于受控发布提交；该提交不改变已验收候选的业务内容，若 SHA 因此变化，必须按既有规则重新取得 main evidence。

## 测试与验收

新增 shell 契约测试覆盖：

- 运行时代码变更缺少 `Unreleased` 时失败；
- 有有效分类和非空条目时通过；
- 缺少分类、空分类或无效标题时失败；
- 纯文档变更时通过；
- 基线缺失或无法解析时失败；
- 每个强制入口都调用共享检查脚本；
- staging 在检查失败时不执行构建、部署或 evidence 写入。

实现后必须通过现有本地质量门禁、全部脚本契约测试，并在 staging 上验证一次拒绝路径和一次通过路径；任何 WARNING 或失败都停止后续发布。
