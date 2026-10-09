# bytedepth Agent 指南

Spring Boot 多模块博客（DDD 分层）与 Obsidian 笔记同步项目。项目知识库入口是 [`docs/README.md`](docs/README.md)，部署边界入口是 [`deploy/README.md`](deploy/README.md)。

## 开发宗旨

这些原则是做技术决策时的优先级，不是装饰性口号：

1. **第一性原理**：先确认用户目标、系统约束和可验证事实，再选择方案；区分需求、假设、推断和证据。
2. **简洁性**：拒绝把简单问题复杂化。优先最小可行改动、最少依赖和最短闭环；没有明确收益的抽象、配置和流程不引入。
3. **奥卡姆剃刀**：多个方案都能满足要求时，选择概念更少、状态更少、维护面更小的方案；复杂度必须由真实约束证明。
4. **适用性**：不照搬流行架构、工具或模板。方案必须适合 bytedepth 当前的模块边界、运行环境、团队能力和发布约束。
5. **可演进优先**：为确定的变化保留清晰边界和可替换接口；避免不可逆的耦合、隐式默认值和一次性捷径。可演进不等于预先建设未来所有能力。
6. **证据优先**：测试、日志、平台状态、完整 SHA 和 artifact digest 高于记忆、猜测和口头结论；没有证据就报告未知，不把推测称为成功。
7. **单一事实源**：配置由配置文件定义，发布由 release-platform 定义，架构取舍由 ADR 定义，领域术语由词汇表定义；发现冲突时修复来源，不复制第二套规则。
8. **边界清晰**：模块、组件、环境和职责必须隔离；AI Agent 修改代码和交接发布，Host Agent 执行平台签名任务，不能混淆。
9. **自动化防复发**：每个已确认的问题都要沉淀为文档规则和可重复检查；不依赖会话记忆、个人经验或口头交接。
10. **失败闭环**：遇到失败先定位根因和边界，再决定修复、重试或停止；重试必须有明确条件、幂等键和终态判断，不能用重复操作掩盖未知状态。

## 每次工作的最短闭环

1. 先读取与任务触发条件匹配的项目文档、ADR 和现有实现。
2. 复杂度属于边界改动、外部接口或长期约束时，先提出设计并获得确认；简单修改直接在隔离 worktree 实施。
3. 修改代码后，先格式化明确变更文件，再跑格式检查、测试和质量门禁；不要只编译不测试。
4. 所有输出中的 `WARNING`、失败和不确定状态都必须处理或报告。
5. 提交前检查 diff、测试证据、文档沉淀和工作区状态；完成后给出可复核的 SHA、命令和结果。

## 不可违反的核心边界

- 不在 `main` 直接开发。功能、修复、文档和格式化改动都在 `feat/*`、`fix/*` 或 `docs/*` worktree 完成，经 PR 合并；合并后的 worktree 及时删除。
- Java 使用仓库 Maven Wrapper、Java 25 和仓库声明的 formatter；前端依赖先按 lockfile 执行 `npm ci --ignore-scripts --no-audit --no-fund`。不得使用裸 `mvn`、浮动运行时或跨项目共享 `node_modules`。
- 格式化必须在 PR 和本地质量门禁前完成：变更文件执行 `bash scripts/format-code.sh <paths>` 与 `bash scripts/format-check.sh <paths>`；全局格式化必须单独执行 `--all`。格式化规范见 [`docs/engineering/code-formatting.md`](docs/engineering/code-formatting.md)。
- 每项代码改动都要有相应测试；本次业务分支覆盖率必须达到项目门禁要求。统一本机入口是 `bash scripts/run-local-quality.sh`。
- 发布只能由 canonical release-platform 和 Host Agent 编排。本仓库不 SSH、不运行部署脚本、不创建发布 Tag、不直接部署 staging/production、不伪造 evidence。
- staging 是唯一集成、E2E 和验收环境；production 只能提升同一已验收的完整 SHA 和不可变 artifact。项目所有者在 Agent 对话中确认后，由 Agent 按平台手册提交 acceptance API；后续只轮询平台状态。
- 任何运行时、用户可见、部署或配置变更都必须有分类明确的 `docs/releases/CHANGELOG.md` `Unreleased` 条目，并按发布流程冻结。
- 不新增 Maven 模块，不越过模块依赖方向，不跨项目污染共享基础设施；需要长期约束或不可逆取舍时使用版本化 ADR。
- 发现流程、配置、测试或部署错误，结束前必须把规则写入项目文档并补自动检查；不能只在回复中说明。

## 按需读取

- **项目导航、文档维护、知识库**：[`docs/README.md`](docs/README.md)
- **模块边界、依赖方向、架构设计**：[`docs/architecture/overview.md`](docs/architecture/overview.md)
- **Maven、Java 25、测试和覆盖率**：[`docs/agent-guides/maven.md`](docs/agent-guides/maven.md)、[`docs/agent-guides/code-quality.md`](docs/agent-guides/code-quality.md)
- **代码格式化与 IntelliJ 对齐**：[`docs/engineering/code-formatting.md`](docs/engineering/code-formatting.md)
- **PR、分支、worktree 和合并**：[`docs/engineering/git-workflow.md`](docs/engineering/git-workflow.md)
- **release-platform API、验收、production 和故障处理**：[`docs/engineering/release-platform-only.md`](docs/engineering/release-platform-only.md)
- **运行时、主机、共享基础设施和 staging 辅助约定**：[`deploy/README.md`](deploy/README.md)、[`docs/engineering/gotchas.md`](docs/engineering/gotchas.md)
- **版本、变更记录、发布和回滚**：[`docs/releases/README.md`](docs/releases/README.md)
- **前端公共组件和资源归属**：[`docs/agent-guides/frontend-components.md`](docs/agent-guides/frontend-components.md)
- **安全、登录、CSRF、后台权限**：[`docs/security/csrf-session-repository.md`](docs/security/csrf-session-repository.md)、[`docs/security/ops.md`](docs/security/ops.md)
- **领域术语或 ADR**：[`docs/architecture/ubiquitous-language.md`](docs/architecture/ubiquitous-language.md)、[`docs/architecture/decisions/README.md`](docs/architecture/decisions/README.md)

更具体的任务规则优先读取对应文档；本文件只保留跨任务都必须知道的原则和边界。
