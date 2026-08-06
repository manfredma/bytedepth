# bytedepth-infrastructure

持久化与基础设施实现层。提供 MyBatis、Redis、MeiliSearch 等端口实现。

**依赖方向：** app, domain

**责任：**
- MyBatis-Plus 数据访问（Post、Comment、User 等）
- Redis 数据访问（统计、限流、阅读进度）
- MeiliSearch 搜索索引
- 安全认证适配（remember-me token）
- 运维监控适配（MySQL、Redis、搜索引擎、部署）