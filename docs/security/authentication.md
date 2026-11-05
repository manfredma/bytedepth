# 会话与认证

网页端使用 Spring Security 表单登录 + CSRF + HttpSession，不引入 JWT。认证与 CSRF 配置位于 `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`。

## Hearth OIDC

Hearth OIDC 是可配置的统一认证入口，使用 `BYTEDEPTH_HEARTH_OIDC_ENABLED=true` 开启。Client registration 名称固定为 `hearth`，由 Hearth issuer、client ID、client secret 和标准回调地址组成；生产环境只通过未跟踪的运行时环境注入 secret。

启用 Hearth 后，用户主动登录进入 `/oauth2/authorization/hearth`；匿名访问受保护 HTML GET 保留原请求并展示后台认证及显式登录入口，API/写请求不自动重放。凭证输入、记住我和认证界面由 Hearth 提供；不得在 bytedepth 继续显示本地登录表单并仅附加统一登录按钮。`/login?oidc=pending`、`/login?oidc=blocked` 和 `/login?error` 仅展示业务认证结果和重新登录入口，不自动重新授权，避免已有 Hearth 会话导致循环。关闭 OIDC 时保留本地登录；移动登录安全链不变。该入口行为由 `HearthOidcSecurityRoutingTest` 验证，部署门禁在隔离测试前及恢复后拒绝 `/login` 返回本地表单或重定向循环。

OIDC 成功后 bytedepth 以当前 issuer 的 `iss + sub` 查找 `user_identity`，首次登录创建无本地密码的 `PENDING` 用户，管理员激活后才能使用本地业务权限。用户名、邮箱和 Hearth 的 `role_key` 不作为跨系统身份或 bytedepth 权限来源。

staging 必须在 Hearth 的真实客户端登记中存在独立的 `bytedepth-staging`，授权回调为 `https://staging-bytedepth.bytedepth.cn/login/oauth2/code/hearth`，退出返回为 `https://staging-bytedepth.bytedepth.cn/login`。运行环境须同时提供启用开关、staging issuer、client ID 和 secret；Hearth 首页的应用卡片不证明 OAuth Client 已登记。secret 只在创建响应中返回一次，必须通过正式基础设施配置流程保存，不能写进仓库、聊天或测试日志。

`deploy/run-staging-e2e-tests.sh` 在测试槽启动前及恢复真实应用后检查授权跳转，并向 Hearth 验证该匿名授权请求可进入登录页；404、错误环境/客户端/回调、缺少 PKCE 或 Hearth 拒绝未登记客户端均阻断 PASS。该检查只证明授权请求被接受，不能校验 client secret 或完成登录；验收仍需真实 Hearth 登录、授权回调、本地 PENDING/封禁处理、权限边界和退出链路的证据。隔离槽中的本地表单登录及通用 integration/E2E PASS 不能替代这些证据。离线回归入口为 `python3 -m tools.bytedepth test`。

Hearth 只负责认证、应用访问和统一登录生命周期。bytedepth 继续从本地 `user_role`、`role_permission` 和 `permission` 加载角色与权限；本地 Session、CSRF、文章所有权和数据授权不变。登出先清理 bytedepth Session，再使用 Hearth 的 RP-Initiated Logout。

转换为本地 `SiteUserDetails` 后，原始且已验证的 OIDC Authentication 只作为本地 Authentication 的服务端 details 保留，用于退出时提供 registration 与 ID token，不参与本地权限计算。退出安全链同时删除本地记住我 Cookie；本地登录及匿名退出不发起 OIDC 退出。`HearthOidcSecurityRoutingTest` 通过实际安全链验证转换后退出、Session 失效及 Cookie 清理。

移动接口使用独立安全链与显式 Session 头，边界见 [ADR-0025](../architecture/decisions/0025-react-native-mobile-reader.md) 与 [移动 API 契约](../architecture/mobile-api.md)。网页 Cookie 不认证移动接口，移动头不认证网页；两者均保留 CSRF。

## 后台自动认证

网页在首次公开访问、导航或从后台恢复时检查本站认证；无需本站历史登录标记。本站未登录且未明确退出时，通过隐藏 iframe 发起一次 `prompt=none`，复用既有 Authorization Code + S256 PKCE、state、nonce 和本地身份映射。主页面不离站；成功核实本站认证后刷新原完整 URL，失败、需要 consent 或二十秒超时均安静结束，保留匿名阅读与显式登录入口。

Web Locks 排队覆盖完整尝试，获锁标签先复查身份，其他标签也能更新登录状态；跨标签页共享五分钟冷却。localStorage 仅保存尝试时间；没有周期保活轮询，不保存身份或 Token。受限浏览器保留显式登录，不偷偷降级为顶层授权。仅 `/auth/silent-complete` 允许 SAMEORIGIN 嵌入；父页面严格验证消息 origin、source 及固定结果，再核实真实本站认证。详细取舍见 [ADR-0027](../architecture/decisions/0027-bounded-oidc-session-recovery.md)。

显式退出下发无身份的 Host-only/HttpOnly 退出抑制标记；旧标签页也不能启动后台授权。退出返回已登记 `/login` 时保留登录入口，只有用户主动登录成功才清除抑制；静默回调不删除它，网页安全链拒绝附带抑制标记的已认证遗留 Session，同时保留匿名显式登录的 state/CSRF。此标记不参与认证或权限判定，不能用它恢复身份。

手动授权等待期间最多五分钟拒绝后台启动；回调分类取自框架已匹配 state 的授权请求，避免不同标签的流程串线。

回归包括 `HearthOidcSecurityRoutingTest`、resolver/流程/网页超时单测和 `auth-recovery.test.js`。Host E2E 在隔离本地表单测试槽清理、真实 staging app 恢复后，使用 `playwright.oidc.config.mjs` 执行桌面/移动 OIDC 专项；专项失败不写 PASS evidence。Cookie-only 首个 Hearth 请求必须是授权，不先访问 `/api/session`。认证专项关闭 trace/视频/截图，输出不含凭据或授权材料。

## 共享 Session

多实例共享 Spring Session Redis，负载均衡无需 sticky session。配置见 `bytedepth-start/src/main/resources/application.yml`：

- 网页：`bytedepth.web-session-timeout-seconds=600`（空闲 10 分钟过期），网页安全链在读取/创建 Session 时设置，保证重定向提交前生效。
- Redis repository 的 `spring.session.timeout=60m` 保留给独立原生移动安全链；网页覆盖不改变移动时长。
- `spring.session.data.redis.namespace`（默认值与环境变量见配置文件）

Spring Boot 4 的 Redis Session 自动配置来自 BOM 管理的 `spring-boot-starter-session-data-redis`；仅依赖原始 `spring-session-data-redis` 不足以注册 Boot 自动配置。不要另写 `@EnableRedisHttpSession` 或复制命名空间/超时作为第二套配置。`SessionConfigurationTest` 验证实际 repository、filter 注册与顺序、命名空间、超时和 Cookie 属性；这是离线配置证据，真实 Redis 存取仍由 staging 集成测试验证。

`staging-it` 和 `staging-e2e` 的 Session 覆盖同样使用 `spring.session.data.redis.namespace`。旧的 `spring.session.redis.namespace` 不会覆盖 Boot 4 的有效前缀。配置回归从真实基础/测试 YAML 加载属性并检查实际 RedisSessionRepository 的 namespace，不能只检查环境变量名出现在文件中；Host IT 再核对有效值与当前测试槽一致。

`SessionIdConfiguration` 使用 Boot 提供的 CookieSerializer 配置网页 delegate，并将按路径选择的 resolver 设为主 resolver。自定义 resolver 时必须保留该 delegate，否则 Boot 的条件配置可能不再生成 CookieSerializer，导致 Secure/HttpOnly/SameSite 配置失效。`RouteSessionIdResolverTest` 与 `MobileSessionLifecycleTest` 检查读取、下发、失效和两种传输互不回退。

测试可执行 JAR 的 Cookie 配置时，`WebApplicationContextRunner` 的默认 MockServletContext 会被识别为 WAR 环境；配置夹具需明确选择 embedded 分支，再验证 `server.servlet.session.cookie.*`，不能靠生产代码硬编码属性使测试通过。

Redis 重启会丢失普通 Session。本地表单模式使用本站签名 Cookie；Hearth 模式由浏览器经后台 OIDC 恢复，本站不读取 Hearth Cookie。

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
