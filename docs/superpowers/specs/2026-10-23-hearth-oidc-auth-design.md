# Hearth OIDC 认证接入设计

## 意图

让 bytedepth 使用 Hearth 提供统一身份认证，同时保留 bytedepth 自己的本地 Session、账号生命周期和 RBAC。首次通过 Hearth 应用访问检查的用户在 bytedepth 建立本地 `PENDING` 账号，管理员激活后才能使用需要本地权限的功能。

## 目标

- 使用 OIDC Authorization Code + PKCE 接入 Hearth。
- 以当前 Hearth issuer 下稳定的 `sub` 建立 bytedepth 本地身份映射；映射键为 `issuer + subject`。
- 首次登录自动创建本地 `PENDING` 用户，并保存非敏感的显示信息。
- OIDC 登录成功后加载 bytedepth 本地用户和权限，继续使用现有 `SiteUserDetails`、Session、CSRF 和 RBAC。
- 登出先清理 bytedepth 本地 Session，再按 Hearth RP-Initiated Logout 返回。
- 缺少本地映射、PENDING、BANNED 和未配置 OIDC 时都有可理解且可测试的结果。

## 非目标

- 不把 Hearth 的 `role_key`、claims 或 `preferred_username` 直接转换为 bytedepth 权限。
- 不共享 Hearth Cookie、Remember-Me Cookie、数据库或 Redis Session。
- 不删除现有本地表单登录、移动端登录或本地权限表。
- 不实现跨业务系统的统一 RBAC。

## 约束

- Hearth 是认证中心；bytedepth 是业务系统，最终授权仍由本地 `user_role`、`role_permission` 和 Controller/用例检查完成。
- OIDC 身份主键只能是 `issuer + subject`，不能用邮箱或用户名自动合并账号。
- OIDC client secret、issuer 和生产回调配置只来自运行时环境，不提交真实凭据。
- 新增运行时配置和用户可见登录行为必须记录在 `docs/releases/CHANGELOG.md` 的 `Unreleased`。
- 代码保持现有 DDD 模块依赖方向，不新增 Maven 模块。

## 方案比较

1. 只在 release-platform 登记 Hearth：不改 bytedepth，无法让 bytedepth 使用统一登录，不满足目标。
2. 用 OIDC principal 直接替换 bytedepth 用户和权限：会把认证身份与业务授权耦合，破坏现有本地 RBAC，不采用。
3. **采用：OIDC 认证 + 本地身份映射**：Hearth 只确认身份，bytedepth 用 `issuer + subject` 维护本地账号映射，并从现有 RBAC 加载权限；边界最清晰且可渐进迁移。

## 关键流程

1. 未登录用户访问需要认证的 Web 路由时进入 Hearth OIDC 授权入口。
2. Spring Security 校验 authorization code、state、PKCE 和 issuer。
3. bytedepth 用 `issuer + sub` 查找 `user_identity`；不存在时创建 `PENDING` 本地用户和映射。
4. bytedepth 将本地用户装载为 `SiteUserDetails`，权限仍来自本地权限表，并保存本地 HttpSession。
5. PENDING/BANNED 用户拒绝建立可用业务会话；管理员激活后再次登录即可生效。
6. 登出清理 bytedepth Session，并将用户带到 Hearth `end_session_endpoint`，回跳 bytedepth 登录页。

## 待实现者决定

- 具体 OIDC client registration 的环境变量命名和默认开发 issuer。
- 本地用户名冲突时的确定性后缀策略。
- OIDC 登录页与现有本地登录页的兼容文案和入口位置。
