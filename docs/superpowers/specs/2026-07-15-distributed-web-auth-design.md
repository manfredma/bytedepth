# 分布式网页认证设计

## 目标

支持多个 Spring Boot 实例共享网页登录状态，并向用户提供可撤销的 30 天“记住我”登录体验。

## 认证模型

- 网页端继续使用 Spring Security 表单登录、CSRF 和 HttpSession；不迁移为 JWT。
- Spring Session Redis 取代本机内存 Session。所有应用实例连接同一个 Redis，任一实例可读取同一 Session。
- Session 空闲 60 分钟后过期。
- 登录页新增非默认勾选的“记住我（30 天）”。勾选后，Spring Security 的持久化 remember-me Token 可在 Session 缺失时恢复认证。
- Redis 重启或清空会丢失所有普通 Session；已勾选记住我的用户会在后续请求中由有效 Token 自动恢复，未勾选的用户需要重新登录。

## 持久化与撤销

- Flyway 新增 `persistent_logins` 表，遵循 Spring Security `JdbcTokenRepositoryImpl` 的列：`username`、`series`、`token`、`last_used`，以 `series` 为主键。
- Token 由 Spring Security 生成、轮换及校验；应用不自行解析或向页面暴露 Token。
- 正常退出调用 `PersistentTokenRepository.removeUserTokens(username)`，撤销该用户的记住我登录。
- 密码修改、封禁等全局撤销事件留作后续用户管理改造，本次不改变既有命令流程。

## 安全配置

- remember-me Cookie 名称为 `bytedepth-remember-me`，有效期 30 天。
- Session Cookie 使用 `HttpOnly`、`SameSite=Lax`；生产环境由配置启用 `Secure`。
- remember-me Cookie 使用 `HttpOnly`、`SameSite=Lax`，在 HTTPS 部署时使用 `Secure`。
- 保持现有 Session 型 CSRF Token Repository、表单登录入口和权限规则不变。

## 配置与依赖

- `bytedepth-start` 新增 `spring-session-data-redis` 与 Spring JDBC 所需依赖。
- `application.yml` 配置 `spring.session.store-type=redis`、60 分钟 timeout、Redis namespace；Cookie 安全属性通过环境变量在本地与生产环境之间切换。
- MySQL 与 Redis 是所有应用实例的共享基础设施；负载均衡无需 sticky session。

## 页面与测试

- 登录页增加 remember-me checkbox，字段名遵循 Spring Security 默认 `remember-me`。
- Security MVC 测试验证未勾选时不请求持久化 Token，勾选时调用 Token Repository；退出时删除 Token。
- 配置测试验证 Redis Session、60 分钟 timeout 和 Cookie 属性。
- 完成改动后按项目约束执行 Java 21 的多模块缓存刷新和完整 Maven 测试。

## 非目标

- 不为移动端或桌面端引入 JWT、refresh token 或 OAuth/OIDC。
- 不增加设备管理页面、密码修改时的全局 Token 撤销或 Redis 高可用拓扑。
