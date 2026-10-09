# 统一发布流程

项目发布已统一迁移到 release-platform；本项目不再提供本地 staging、production、Tag 或 Docker 发布入口。

通过 release-platform 页面执行：绑定代码账户 → PR head webhook 创建候选 → QUALITY/BUILD → 发布 staging → 页面验收 → 合并同一 SHA → 校验 main HEAD → 将同一不可变制品提升到 production。bytedepth 的既有 `bytedepth-start` 运行模块由平台适配器识别，项目源码不需要改造成 release-platform 的模块名称。

具体操作见 [release-platform 发布说明](release-platform-only.md)。项目所有者在 Agent 对话中回复“验收通过”后，由 AI Agent 提交 acceptance API；平台 Worker 自动完成 production 晋级，业务 Agent 不执行 SSH 或项目部署脚本。
