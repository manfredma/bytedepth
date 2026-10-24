# 会话与认证

网页端使用 Spring Security 表单登录 + CSRF + HttpSession，不引入 JWT。认证与 CSRF 配置位于 `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`。

## Hearth OIDC

Hearth OIDC 是可配置的统一认证入口，使用 `BYTEDEPTH_HEARTH_OIDC_ENABLED=true` 开启。Client registration 名称固定为 `hearth`，由 Hearth issuer、client ID、client secret 和标准回调地址组成；生产环境只通过未跟踪的运行时环境注入 secret。

启用 Hearth 后，网页正常登录入口 `/login` 以及匿名访问受保护网页均直接进入 `/oauth2/authorization/hearth`，凭证输入、记住我和认证界面由 Hearth 提供；不得在 bytedepth 继续显示本地登录表单并仅附加统一登录按钮。`/login?oidc=pending`、`/login?oidc=blocked` 和 `/login?error` 仅展示业务认证结果和重新登录入口，不自动重新授权，避免已有 Hearth 会话导致循环。关闭 OIDC 时保留本地登录；移动登录安全链不变。该入口行为由 `HearthOidcSecurityRoutingTest` 验证，部署门禁在隔离测试前及恢复后拒绝 `/login` 返回本地表单或重定向循环。

OIDC 成功后 bytedepth 以当前 issuer 的 `iss + sub` 查找 `user_identity`，首次登录创建无本地密码的 `PENDING` 用户，管理员激活后才能使用本地业务权限。用户名、邮箱和 Hearth 的 `role_key` 不作为跨系统身份或 bytedepth 权限来源。

staging 必须在 Hearth 的真实客户端登记中存在独立的 `bytedepth-staging`，授权回调为 `https://staging-bytedepth.bytedepth.cn/login/oauth2/code/hearth`，退出返回为 `https://staging-bytedepth.bytedepth.cn/login`。运行环境须同时提供启用开关、staging issuer、client ID 和 secret；Hearth 首页的应用卡片不证明 OAuth Client 已登记。secret 只在创建响应中返回一次，必须通过正式基础设施配置流程保存，不能写进仓库、聊天或测试日志。

`deploy/run-staging-e2e-tests.sh` 在测试槽启动前及恢复真实应用后检查授权跳转，并向 Hearth 验证该匿名授权请求可进入登录页；404、错误环境/客户端/回调、缺少 PKCE 或 Hearth 拒绝未登记客户端均阻断 PASS。该检查只证明授权请求被接受，不能校验 client secret 或完成登录；验收仍需真实 Hearth 登录、授权回调、本地 PENDING/封禁处理、权限边界和退出链路的证据。隔离槽中的本地表单登录及通用 integration/E2E PASS 不能替代这些证据。离线回归入口为 `bash scripts/test-run-staging-e2e-tests.sh`。

Hearth 只负责认证、应用访问和统一登录生命周期。bytedepth 继续从本地 `user_role`、`role_permission` 和 `permission` 加载角色与权限；本地 Session、CSRF、文章所有权和数据授权不变。登出先清理 bytedepth Session，再使用 Hearth 的 RP-Initiated Logout。

转换为本地 `SiteUserDetails` 后，原始且已验证的 OIDC Authentication 只作为本地 Authentication 的服务端 details 保留，用于退出时提供 registration 与 ID token，不参与本地权限计算。退出安全链同时删除本地记住我 Cookie；本地登录及匿名退出不发起 OIDC 退出。`HearthOidcSecurityRoutingTest` 通过实际安全链验证转换后退出、Session 失效及 Cookie 清理。

移动接口使用独立安全链与显式 Session 头，边界见 [ADR-0025](../architecture/decisions/0025-react-native-mobile-reader.md) 与 [移动 API 契约](../architecture/mobile-api.md)。网页 Cookie 不认证移动接口，移动头不认证网页；两者均保留 CSRF。

## 共享 Session

多实例共享 Spring Session Redis，负载均衡无需 sticky session。配置见 `bytedepth-start/src/main/resources/application.yml`：

- `spring.session.timeout=60m`（空闲 60 分钟过期）
- `spring.session.data.redis.namespace`（默认值与环境变量见配置文件）

Spring Boot 4 的 Redis Session 自动配置来自 BOM 管理的 `spring-boot-starter-session-data-redis`；仅依赖原始 `spring-session-data-redis` 不足以注册 Boot 自动配置。不要另写 `@EnableRedisHttpSession` 或复制命名空间/超时作为第二套配置。`SessionConfigurationTest` 验证实际 repository、filter 注册与顺序、命名空间、超时和 Cookie 属性；这是离线配置证据，真实 Redis 存取仍由 staging 集成测试验证。

`staging-it` 和 `staging-e2e` 的 Session 覆盖同样使用 `spring.session.data.redis.namespace`。旧的 `spring.session.redis.namespace` 不会覆盖 Boot 4 的有效前缀。配置回归从真实基础/测试 YAML 加载属性并检查实际 RedisSessionRepository 的 namespace，不能只检查环境变量名出现在文件中；Host IT 再核对有效值与当前测试槽一致。

`SessionIdConfiguration` 使用 Boot 提供的 CookieSerializer 配置网页 delegate，并将按路径选择的 resolver 设为主 resolver。自定义 resolver 时必须保留该 delegate，否则 Boot 的条件配置可能不再生成 CookieSerializer，导致 Secure/HttpOnly/SameSite 配置失效。`RouteSessionIdResolverTest` 与 `MobileSessionLifecycleTest` 检查读取、下发、失效和两种传输互不回退。

测试可执行 JAR 的 Cookie 配置时，`WebApplicationContextRunner` 的默认 MockServletContext 会被识别为 WAR 环境；配置夹具需明确选择 embedded 分支，再验证 `server.servlet.session.cookie.*`，不能靠生产代码硬编码属性使测试通过。

Redis 重启会丢失普通 Session；勾选记住我的用户靠签名 cookie 恢复登录，无需重新登录。

移动端不使用 remember-me。先获取 `/api/mobile/v1/csrf`，再提交标准登录表单；成功接收轮换后的 `X-App-Session` 并重新获取 CSRF。会话失效重新登录，CSRF 失效不自动重放写入。Session principal 沿用登录快照，不将 `/me` 描述为已有即时封禁撤销能力。

路由隔离与限流以一致的解析后路径判断，编码的移动路径不能落入 Cookie delegate 或绕过配额。`RouteSessionIdResolverTest`、`RateLimitFilterTest` 和移动路由测试保留编码路径回归；框架拒绝请求时也返回移动 JSON 错误。

客户端异步请求携带开始时的 Session 与私有状态 generation，在持久化前后和响应解码后重新核对；Session 轮换不能让旧请求取得新 generation 并恢复身份。退出清理按账号 generation 判断，后台或离线导致的私有层清空不能阻止凭据删除，也不能误删之后登录的新账号。SessionStore、MobileApi 与 AuthStore 的竞态测试约束这些顺序。

## 记住我

实际实现为无状态 `TokenBasedRememberMeServices`——自包含签名 cookie（用户名 + 过期时间 + HMAC），不依赖数据库存储。历史设计曾用 JDBC `PersistentTokenRepositoryImpl` + `persistent_logins` 表，V13 建表后 V23 删除，已改为无状态方案。

| 配置      | 值                                                                               |
| --------- | -------------------------------------------------------------------------------- |
| Cookie 名 | `bytedepth-remember-me`                                                          |
| 表单参数  | `remember-me`                                                                    |
| 有效期    | 30 天（`30 * 24 * 60 * 60` 秒）                                                  |
| HttpOnly  | 是（Spring 默认）                                                                |
| SameSite  | `Lax`                                                                            |
| Secure    | 由 `BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE` 控制，默认 `false`，HTTPS 部署设 `true` |
| 签名密钥  | 由 `BYTEDEPTH_REMEMBER_ME_KEY` 注入，默认本地值不可用于生产                      |

无状态方案在 Session 过期后，浏览器并发请求各自校验同一 cookie，不会因 token 轮换互相失效。

## 约束

- 不要重新引入基于数据库表的记住我实现；如需可撤销的记住我，应另选方案并同步更新本文。
- 生产环境必须配置 `BYTEDEPTH_REMEMBER_ME_KEY` 与 `BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE=true`。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](csrf-session-repository.md)。
- 权限模型与授权执行见 [角色与权限模型](rbac.md)。
