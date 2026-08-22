# 会话与认证

网页端使用 Spring Security 表单登录 + CSRF + HttpSession，不引入 JWT。认证与 CSRF 配置位于 `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`。

## 共享 Session

多实例共享 Spring Session Redis，负载均衡无需 sticky session。配置见 `bytedepth-start/src/main/resources/application.yml`：

- `spring.session.store-type=redis`
- `spring.session.timeout=60m`（空闲 60 分钟过期）
- `spring.session.redis.namespace=bytedepth:session:v2`

Redis 重启会丢失普通 Session；勾选记住我的用户靠签名 cookie 恢复登录，无需重新登录。

## 记住我

实际实现为无状态 `TokenBasedRememberMeServices`——自包含签名 cookie（用户名 + 过期时间 + HMAC），不依赖数据库存储。历史设计曾用 JDBC `PersistentTokenRepositoryImpl` + `persistent_logins` 表，V13 建表后 V23 删除，已改为无状态方案。

| 配置 | 值 |
| --- | --- |
| Cookie 名 | `bytedepth-remember-me` |
| 表单参数 | `remember-me` |
| 有效期 | 30 天（`30 * 24 * 60 * 60` 秒） |
| HttpOnly | 是（Spring 默认） |
| SameSite | `Lax` |
| Secure | 由 `BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE` 控制，默认 `false`，HTTPS 部署设 `true` |
| 签名密钥 | 由 `BYTEDEPTH_REMEMBER_ME_KEY` 注入，默认本地值不可用于生产 |

无状态方案在 Session 过期后，浏览器并发请求各自校验同一 cookie，不会因 token 轮换互相失效。

## 约束

- 不要重新引入基于数据库表的记住我实现；如需可撤销的记住我，应另选方案并同步更新本文。
- 生产环境必须配置 `BYTEDEPTH_REMEMBER_ME_KEY` 与 `BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE=true`。
- CSRF 仓库选型与历史故障见 [CSRF 决策记录](csrf-session-repository.md)。
- 权限模型与授权执行见 [角色与权限模型](rbac.md)。
