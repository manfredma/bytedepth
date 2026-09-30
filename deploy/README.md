# 部署说明

ByteDepth 的 staging、production 构建、制品生成、环境绑定、发布、验证和回滚统一由 release-platform 负责。

本仓库不再提供可执行的发布入口，也不允许从项目工作区直接发布。保留的 `deploy/` 内容只服务于运行时依赖准备、集成测试和 E2E 测试，由 release-platform 的 Host Agent 按平台编排调用，不是人工发布入口。

## 发布操作

1. 在 release-platform 的“项目”页面绑定 `manfredma/bytedepth` 和可用代码账户。
2. 确认 staging 与 production 环境绑定既有主机；不要在 ByteDepth 仓库中修改主机、目录或 systemd 配置。
3. 在“构建”页面输入完整 commit SHA，查看 QUALITY、BUILD 日志和制品摘要。
4. 在构建详情页创建 staging 发布，等待部署、验证和页面验收证据完成。
5. staging 验收通过后，在“发布”页面将同一不可变制品提升到 production。

平台适配器负责识别现有 `bytedepth-start` 模块布局，并沿用既有 native 运行时、端口、数据目录和 systemd unit。项目源码不需要改名或新增发布脚本；历史发布说明只用于追溯，不得照此执行。
