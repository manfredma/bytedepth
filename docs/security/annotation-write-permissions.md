# 批注与划线写操作权限管理（长期 TODO）

> 状态：长期规划，未实施。记录当前权限模型与待补强方向，供后续迭代决策。
> 权威入口：批注写操作的权限语义以此文为准；身份机制见 [CSRF 与会话存储](csrf-session-repository.md)，匿名读者概念见 [统一语言](../architecture/ubiquitous-language.md)。

## 现状（截至 v2.0.0）

批注写操作（创建/编辑/删除划线与评注）已具备双轨归属模型，但权限治理仍有缺口。

### 已实现

- **双轨身份归属**
  - 登录用户：`SecurityUtils.extractUserId(currentUser)` → `PostAnnotation.userId`
  - 匿名读者：`bd_annotation_visitor` HttpOnly Cookie，服务端仅存 SHA-256 hash（不可逆），见 `AnnotationVisitorIdentity`
- **归属校验**：`DeleteAnnotationCmdExe` / `UpdateAnnotationCmdExe` 校验 `ownedByUser || ownedByVisitor`，非归属者抛错
- **可见性**：`AnnotationVisibility`（PUBLIC / PRIVATE）；`ListAnnotationsQryExe` 返回公开批注 + 当前读者自己的私有划线
- **前端门控**：`PostAnnotationDTO.ownedByCurrentVisitor` 控制编辑/删除按钮显隐
- **访问放行**：`SecurityConfig` 对 `/posts/*/annotations` 未显式声明，默认 permitAll——匿名写是产品功能

### 缺口（待补强）

1. **写操作未限流**：`RateLimitFilter` 已覆盖 `login/register/comment-rating/upload`，但批注 POST/PATCH/DELETE 无规则。匿名端点无限流 = 垃圾批注/滥用向量，违反 AGENTS.md「所有端点必须限流」。
2. **匿名归属不可恢复**：归属绑定在浏览器 Cookie 上，Cookie 清除即失去对自己批注的编辑/删除权，产生孤儿批注；无认领或迁移机制。
3. **无管理员处置能力**：管理员无法删除/隐藏他人公开批注，缺内容治理入口（参考 [后台布局](../architecture/admin-layout.md)）。
4. **公开批注编辑策略未文档化**：当前仅 owner 可改 PUBLIC 批注；是否允许协作、是否仅作者可改需明确并写入统一语言。
5. **匿名跨设备**：visitor 仅 per-browser，无法跨设备访问自己的批注。

## 实施时需决策的开放问题

- 匿名写操作是否收紧为要求登录？还是保持开放并依赖限流 + 审计？
- 限流维度：per-visitor-token / per-IP / per-user？容量与周期？
- 孤儿批注保留策略与清理周期？是否允许匿名→登录后认领历史批注？
- 管理员处置批注的 UI 入口放在后台何处（与现有 [运维页面](ops.md) 边界对齐）？
- 公开批注被作者删除后，已有引用如何处理？

## 关联

- 身份与 Cookie 机制：[CSRF 与会话存储](csrf-session-repository.md)
- 匿名读者/作者概念边界：[统一语言](../architecture/ubiquitous-language.md)
- 后台权限模型（RBAC）：[角色与权限模型](rbac.md)
- 限流框架：`bytedepth-adapter` 的 `RateLimitFilter` / `RateLimitProperties`（`bytedepth.rate-limit.*`）
