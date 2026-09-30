# 发布与回滚

bytedepth 的构建、发布、验收和回滚统一由 release-platform 管理。项目仓库不再提供发布 Tag、staging/production 部署脚本或手工生产入口。

操作路径见 [release-platform 发布说明](../engineering/release-platform-only.md)。生产发布只能提升已在 staging 页面验收通过的同一不可变制品。
