# 发布

ByteDepth 的发布生命周期完全由 release-platform 管理。本仓库不创建发布 Tag，不维护 staging/production 发布脚本，也不直接执行生产回滚。

统一流程：

`完整 commit SHA → QUALITY → BUILD → staging 发布 → 页面验收 → 同一制品提升 production`

构建、发布、日志、重试、验证和回滚都必须在 release-platform 页面完成，并以页面中的候选、制品和任务记录为准。
