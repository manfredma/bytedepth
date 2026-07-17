# 账户测试可移植性设计

## 决策

删除 `AccountFlowE2ETest`。它的七项断言在默认环境因 Docker 不可用整体跳过，且注册、重复注册、用户激活和后台审核已有 Controller 或命令测试覆盖。

不以 H2 替代 MySQL：现有 Flyway 迁移依赖 MySQL 方言，维护另一套测试迁移会增加不成比例的维护成本。

## 保留的覆盖

新增一个 Web MVC 安全测试，加载真实 `SecurityConfig` 及 Spring Security 过滤器链、但以 mock 隔离数据库和评论业务依赖。它验证匿名 `POST /posts/{slug}/comments` 被安全配置重定向到 `/login`，而不是进入控制器。

## 验收

- 默认 Maven 测试不再发现或跳过 `AccountFlowE2ETest`。
- 匿名评论安全路由在不启动 Docker、MySQL、Redis 或 Meilisearch 的情况下被自动验证。
- 完整 Maven 测试通过且没有新增跳过的测试。
