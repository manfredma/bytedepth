# bytedepth-start

Spring Boot 启动入口与配置。集成所有模块，提供 Flyway 迁移、静态资源与跨模块集成测试。

**依赖方向：** adapter, infrastructure

**责任：**
- Spring Boot 启动类
- 应用配置（application.yml）
- 静态资源（CSS、JS、图片）
- Flyway 数据库迁移
- 跨模块集成测试（Controller 测试、架构守护测试）