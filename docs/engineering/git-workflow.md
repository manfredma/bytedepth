# Git 工作流

ByteDepth 使用 GitHub Flow。功能、修复和文档改动从最新 `origin/main` 创建短生命周期分支，经本地质量检查和 PR 合并；发布不在项目仓库中执行。

1. 从最新 `origin/main` 创建 `feat/*`、`fix/*` 或 `docs/*` 分支。
2. 本地只运行项目质量检查，不创建发布 Tag，不执行远程部署脚本。
3. PR 合并后，以完整 commit SHA 在 release-platform 创建候选并启动 `BUILD` flow；不依赖 GitHub Actions quality。
4. 由 release-platform 编排 staging 发布、日志查看、重试、页面验收和 production 提升。
5. production 必须提升已经在 staging 验收通过的同一不可变制品。

项目仓库中保留的测试或运行时辅助脚本，不构成发布入口；它们只能由平台适配器或质量流程按约定调用。
