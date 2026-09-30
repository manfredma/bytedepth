# 通过 release-platform 发布

bytedepth 保持现有业务源码和 `bytedepth-start` 运行模块不变；旧的 staging/production 发布入口、环境安装入口和 release prepare/verify 脚本已删除。项目质量检查仍在仓库内执行，发布脚本由 release-platform 的项目适配器和 Host Agent 统一维护。

发布流程：

1. 在 release-platform 的“项目”页面绑定 `manfredma/bytedepth` 和 `personal-github-release-platform` 代码账户；
2. 确认 staging/production 绑定现有主机，不迁移部署目标；
3. 在“构建”页面选择完整 commit SHA，质量阶段从平台绑定的 GitHub 账户读取私有仓库；
4. 通过构建详情发布 staging，查看日志、`/version`、健康检查和验收证据；
5. staging 验收后，在“发布”页面提升同一不可变制品到 production。

不得从 bytedepth 工作区直接执行发布命令；项目发布事实以 release-platform 页面和审计记录为准。
