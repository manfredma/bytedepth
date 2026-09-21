# 项目知识库

本目录存放随代码演进、需要审查和复现的项目知识。入口只保留稳定结论与导航；具体操作和实现细节按需打开，避免把会话记录、工具缓存或历史草稿当作规范。

## 从这里开始

- 新成员或新会话：先读本文，再按任务打开对应主题。
- 修改模块边界或实现方式：读 [架构概览](architecture/overview.md)。
- 评审架构取舍或新增长期技术约束：读 [架构决策记录（ADR）](architecture/decisions/README.md)。
- 查询数据库表结构、字段与表间关系：读 [数据库表结构](architecture/database-schema.md)。
- 讨论全局业务概念、限界上下文和名称边界：读 [统一语言](architecture/ubiquitous-language.md)。
- 新增后台页面：读 [后台布局](architecture/admin-layout.md)。
- 修改前端页面、组件、视觉样式或交互：**必须先读** [前端设计语言](architecture/frontend-design-language.md)，再按需读 [前端公共组件模式](engineering/frontend-patterns.md) 与 [前端组件约束](agent-guides/frontend-components.md)。
- 修改分页：读 [前端模式](engineering/frontend-patterns.md)。
- 查询所有路由：读 [路由一览](architecture/routes.md)。
- 排查已知问题：读 [工程陷阱](engineering/gotchas.md)。
- 管理尚未处理的技术债：读 [技术债清单](engineering/technical-debt.md)。
- 修改访问日志、阅读统计或首页排序：读 [访问日志与统计](engineering/view-log-and-analytics.md)。
- 修改登录、表单或 CSRF：读 [CSRF 决策记录](security/csrf-session-repository.md)。
- 修改会话、记住我或 Session 共享：读 [会话与认证](security/authentication.md)。
- 新增后台权限或调整授权分层：读 [角色与权限模型](security/rbac.md)。
- 批注与划线写操作的权限管理：读 [批注写操作权限管理](security/annotation-write-permissions.md)。
- 构建、测试、覆盖率：读 [Maven 指南](agent-guides/maven.md)。
- 创建 worktree、分支、PR 与受控发布：读 [Git 工作流](engineering/git-workflow.md)。
- 新项目接入与执行共同质量、staging、Tag 和生产流程：读 [统一发布流程](engineering/unified-release-pipeline.md)。
- 同步 Obsidian 笔记：读 [同步指南](agent-guides/obsidian-sync.md)。
- 部署、回滚和生产与 staging 验收：只读 [部署手册](../deploy/README.md)。
- 创建版本、记录变更、发布与回滚：读 [发布管理](releases/README.md)。
- 了解后台运维页面的权限边界：读 [运维页面说明](security/ops.md)。
- 维护知识库本身（新增、修改或删除文档）：读 [知识库建设原则](knowledge-base-principles.md)。

## 目录边界

| 目录 | 内容 | 不应放入 |
| --- | --- | --- |
| `architecture/` | 模块边界、组件契约、设计约束 | 逐次实现过程 |
| `engineering/` | 可复用开发模式、长期有效的故障经验 | 临时排查日志 |
| `security/` | 安全决策与事故复盘 | 密钥、Cookie、真实账号信息 |
| `releases/` | 版本策略、发布记录与变更日志 | 机器私有部署日志或密钥 |
| `agent-guides/` | 人与自动化代理共同遵守的任务操作指南 | 通用架构说明 |

## 维护规则

知识库建设的完整原则见 [知识库建设原则](knowledge-base-principles.md)。要点：

1. 以当前代码和唯一操作手册为准；无法验证的不迁入。
2. 一个主题一个权威入口；其余用链接，不复制。
3. 入口只放结论与链接，细节按需展开（渐进式披露）。
4. 架构决策、组件契约、故障复盘随代码一并提交。
5. `.omc/`、`.superpowers/` 等工具目录不入知识库。

## 工程术语

- **契约（contract）**：跨模块、跨服务或面向消费者的接口、数据格式与可观察行为约定；例如 API 契约、事件契约和契约测试。不得把一般构建、部署或流程规则称为“契约”。
- **约束（constraint）**：工程执行必须满足的规则；例如固定 Maven 版本、staging-only 测试边界和零 WARNING 门禁。
- **自动化约束**：可由脚本、静态检查或质量门禁自动验证的工程约束。它把自然语言流程要求落实为可重复执行、失败即阻断的检查，以避免只靠执行者记忆。它不等同于面向 API/消费者的契约测试。
- **运行时约束检查**：将已确认的工程运行时规则写成可重复执行的检查；它属于可执行规范/策略即代码，不是 API 的契约测试。当前覆盖 Maven Wrapper、固定 Maven 容器版本与 staging 预热—离线集成测试的一致性，详见 [ADR-0005](architecture/decisions/0005-pinned-maven-runtime.md)。
