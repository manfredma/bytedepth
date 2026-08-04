# 项目知识库

本目录存放随代码演进、需要审查和复现的项目知识。入口只保留稳定结论与导航；具体操作和实现细节按需打开，避免把会话记录、工具缓存或历史草稿当作规范。

## 从这里开始

- 新成员或新会话：先读本文，再按任务打开对应主题。
- 修改模块边界或实现方式：读 [架构概览](architecture/overview.md)。
- 新增后台页面：读 [后台布局](architecture/admin-layout.md)。
- 修改分页：读 [前端模式](engineering/frontend-patterns.md)。
- 排查已知问题：读 [工程陷阱](engineering/gotchas.md)。
- 修改登录、表单或 CSRF：读 [CSRF 决策记录](security/csrf-session-repository.md)。
- 构建、测试、覆盖率：读 [Maven 指南](agent-guides/maven.md)。
- 同步 Obsidian 笔记：读 [同步指南](agent-guides/obsidian-sync.md)。
- 部署、回滚和双机验收：只读 [部署手册](../deploy/README.md)。
- 了解后台运维页面的权限边界：读 [运维页面说明](ops.md)。

## 目录边界

| 目录 | 内容 | 不应放入 |
| --- | --- | --- |
| `architecture/` | 模块边界、组件契约、设计约束 | 逐次实现过程 |
| `engineering/` | 可复用开发模式、长期有效的故障经验 | 临时排查日志 |
| `security/` | 安全决策与事故复盘 | 密钥、Cookie、真实账号信息 |
| `agent-guides/` | 人与自动化代理共同遵守的任务操作指南 | 通用架构说明 |
| `superpowers/` | 已完成需求的设计与计划历史 | 当前操作手册 |

## 维护规则

1. 文档以当前代码和唯一操作手册为准；无法验证的历史说明不迁入。
2. 一个主题只有一个权威入口。部署以 `deploy/README.md` 为准，构建测试以 `agent-guides/maven.md` 为准，笔记同步以 `agent-guides/obsidian-sync.md` 为准。
3. 先写摘要、约束和链接；只有执行任务所需的细节才放入下层文档。这是渐进式披露，不以重复粘贴换取“完整”。
4. 架构决策、公共组件契约与会影响后续改动的故障复盘必须随代码一并提交。
5. `.omc/`、根目录 `.superpowers/` 等工具运行目录只保存本机状态，永不作为项目知识库。
