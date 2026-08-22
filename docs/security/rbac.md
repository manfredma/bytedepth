# 角色与权限模型

bytedepth 使用动态 RBAC：角色与权限在数据库中维护，新增权限项只插入数据、不改代码，`UserDetailsService` 动态加载即时生效。

## 数据模型

`user` ↔ `user_role` ↔ `role` ↔ `role_permission` ↔ `permission`。单一 `user` 表（合并了早期 `admin_user`），状态字段管理账号生命周期，角色通过关联表挂载。

## 分层约束

- 领域层（`bytedepth-domain`）不感知 RBAC 概念。`Role`、`Permission` 是基础设施关注点；领域层只关心 `User` 的状态流转。
- 授权在两层执行：
  - URL 规则（`SecurityConfig` 的 `authorizeHttpRequests`）做粗粒度守卫，如 `/admin/**` 要求 `admin:dashboard:view`；
  - 方法级 `@PreAuthorize` 做细粒度授权（`@EnableMethodSecurity` 已开启）。
- 后台路由的权限代码见 [路由一览](../architecture/routes.md) 的权限段落。

## 不可逆决策

- 评论无审核流：注册用户的评论直接 `APPROVED`，不存在 `approve()`/`reject()` 流程，也不保留匿名评论机制。
- 注册拒绝时直接删除 `PENDING` 记录，不引入 `REJECTED` 状态，避免垃圾数据堆积。

## 约束

- 新增后台能力时，先在 `permission` 表插入权限项并绑定 `ADMIN`，再在 Controller 方法上加 `@PreAuthorize`；不要只靠 URL 规则或页面隐藏来控制授权。
- 所有权校验由 `ContentOwnershipGuard` 在 Web 侧保证，不能仅用页面隐藏代替；详见 [统一语言](../architecture/ubiquitous-language.md) 的所有权条目。
- 运维页面与受控部署的权限边界见 [运维页面说明](ops.md)。
- 会话与记住我实现见 [会话与认证](authentication.md)。
