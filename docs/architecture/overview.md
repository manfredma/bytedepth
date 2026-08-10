# 架构概览

bytedepth 是 Spring Boot 多模块博客，使用 Thymeleaf 服务端渲染，内容可由 Obsidian 笔记同步导入。生产为数据节点与应用节点双机拓扑；具体部署流程以 [部署手册](../../deploy/README.md) 为准。

## 技术边界

- 构建、测试必须使用 JDK 25；项目产物目标为 Java 25。Maven 与覆盖率要求见 [Maven 指南](../agent-guides/maven.md)。
- Spring Boot、Spring Security、Thymeleaf。
- MyBatis-Plus + MySQL；Redis 用于会话与业务能力；MeiliSearch 用于搜索。
- Flyway 管理数据库迁移。

## 模块和依赖方向

```text
adapter ───────▶ app ───────▶ domain
                    ▲            ▲
infrastructure ────┴────────────┘
start ───────────▶ adapter + infrastructure
```

| 模块 | 责任 |
| --- | --- |
| `bytedepth-domain` | 领域模型和 Repository 抽象；不依赖框架或持久化 API。 |
| `bytedepth-app` | 查询/命令用例、DTO 与端口；只依赖领域层。 |
| `bytedepth-infrastructure` | MyBatis、Redis、搜索等端口实现；依赖 app 和 domain。 |
| `bytedepth-adapter` | Web Controller、页面渲染、输入适配与安全配置；依赖 app，不直接使用持久化或 Redis API。 |
| `bytedepth-start` | Spring Boot 启动、配置、数据库迁移和跨模块测试。 |

## 架构守护

`bytedepth-start/src/test/java/manfred/bytedepth/architecture/ArchitectureTest.java` 在完整测试中执行，且没有豁免名单。它禁止：

- domain 依赖 app、infrastructure、adapter、Spring、MyBatis、JPA 或 Servlet API；
- app 依赖 infrastructure 或 adapter；
- infrastructure 依赖 adapter；
- adapter 依赖 infrastructure、MyBatis、JDBC、Redis、Lettuce 或 Bucket4j API。

跨层能力必须在 app 定义端口，再由 infrastructure 实现。需要改变边界时，应先调整模型与测试，而不是增加例外。

## 用例约定

- 查询用例使用 `*QryExe`，命令用例使用 `*CmdExe`。
- 分页返回 `PageResult`，方法命名为 `findPage(page, size)`；`findAll` 只表示无分页的全部结果。
- Controller 只编排 Web 输入输出和调用用例；所有权及细粒度权限校验在 Controller 和服务边界共同保证。

## 延伸阅读

- [后台布局](admin-layout.md)
- [前端模式](../engineering/frontend-patterns.md)
- [CSRF 决策记录](../security/csrf-session-repository.md)
- [工程陷阱](../engineering/gotchas.md)
