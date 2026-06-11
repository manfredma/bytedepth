# 账户系统与 RBAC 权限设计

**日期**：2026-06-11  
**状态**：已确认，待实现  
**范围**：用户注册/登录/审核、动态 RBAC 权限、评论与文章归属、个人主页

---

## 背景

bytedepth 当前仅支持单一管理员账号（`admin_user` 表，ROLE_ADMIN），评论完全匿名。本次扩展为多用户博客平台：注册用户经管理员审核后可发表文章、创建专栏、发表评论；采用动态 RBAC 管理权限，为未来扩展新模块（论坛等）预留空间。

---

## 核心决策

| 决策点 | 结论 |
|---|---|
| 账号模型 | 合并为单一 `user` 表，角色通过 `user_role` 关联 |
| 权限管理 | 动态 RBAC（role/permission/role_permission 三表），运行时从 DB 加载 |
| 权限执行方式 | Spring Security `@PreAuthorize` 方法级注解 |
| 注册审核 | 注册后 status=PENDING，管理员审核通过后 status=ACTIVE 并赋 USER 角色 |
| 评论 | 仅注册用户可评论，直接公开（status=APPROVED），彻底移除匿名评论 |
| 文章发布 | 用户可自主发布到个人主页；上首页推荐需管理员操作（featured=true） |
| 个人主页 | `/u/{username}`，展示用户文章列表和专栏列表 |

---

## 一、数据库模型

### 1.1 新增表

```sql
-- 统一用户表（替换 admin_user）
CREATE TABLE user (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(50)  NOT NULL UNIQUE,
  password    VARCHAR(100) NOT NULL,           -- BCrypt hash
  email       VARCHAR(100),
  avatar      VARCHAR(255),
  bio         TEXT,
  status      ENUM('PENDING','ACTIVE','BANNED') NOT NULL DEFAULT 'PENDING',
  created_at  DATETIME NOT NULL,
  updated_at  DATETIME NOT NULL
);

-- 角色表
CREATE TABLE role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(50)  NOT NULL UNIQUE,    -- ADMIN / USER
  description VARCHAR(255),
  created_at  DATETIME NOT NULL
);

-- 权限表
CREATE TABLE permission (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(100) NOT NULL UNIQUE,    -- e.g. blog:post:create
  description VARCHAR(255),
  module      VARCHAR(50)  NOT NULL,           -- blog / project / system / admin
  created_at  DATETIME NOT NULL
);

-- 角色-权限关联
CREATE TABLE role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

-- 用户-角色关联
CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
```

### 1.2 现有表变更

```sql
-- post 表新增两列
ALTER TABLE post ADD COLUMN author_id BIGINT NOT NULL AFTER id;
ALTER TABLE post ADD COLUMN featured  BOOLEAN NOT NULL DEFAULT FALSE;

-- comment 表：移除匿名字段，改为关联用户
ALTER TABLE comment DROP COLUMN author_name;
ALTER TABLE comment DROP COLUMN author_email;
ALTER TABLE comment ADD COLUMN author_id BIGINT NOT NULL AFTER post_id;
-- 评论不再有 PENDING 状态，注册用户评论直接 APPROVED
```

### 1.3 初始权限种子数据

| code | module | 说明 | USER | ADMIN |
|---|---|---|---|---|
| `blog:post:create` | blog | 创建文章 | ✅ | ✅ |
| `blog:post:edit:own` | blog | 编辑自己的文章 | ✅ | ✅ |
| `blog:post:delete:own` | blog | 删除自己的文章 | ✅ | ✅ |
| `blog:post:publish:own` | blog | 发布自己的文章 | ✅ | ✅ |
| `blog:post:feature` | blog | 设为首页推荐 | ❌ | ✅ |
| `blog:post:manage` | blog | 管理任意文章 | ❌ | ✅ |
| `blog:comment:create` | blog | 发表评论 | ✅ | ✅ |
| `blog:comment:manage` | blog | 管理任意评论 | ❌ | ✅ |
| `blog:series:create:own` | blog | 创建专栏 | ✅ | ✅ |
| `blog:series:edit:own` | blog | 编辑自己的专栏 | ✅ | ✅ |
| `blog:series:manage` | blog | 管理任意专栏 | ❌ | ✅ |
| `blog:category:manage` | blog | 管理分类 | ❌ | ✅ |
| `blog:tag:manage` | blog | 管理标签 | ❌ | ✅ |
| `project:manage` | project | 管理项目 | ❌ | ✅ |
| `system:user:approve` | system | 审核用户注册 | ❌ | ✅ |
| `system:user:manage` | system | 封禁/管理用户 | ❌ | ✅ |
| `system:role:manage` | system | 管理角色与权限 | ❌ | ✅ |
| `admin:dashboard:view` | admin | 访问后台仪表盘 | ❌ | ✅ |

### 1.4 数据迁移 SQL

```sql
-- 1. admin_user 迁移到 user 表
INSERT INTO user (username, password, status, created_at, updated_at)
SELECT username, password, 'ACTIVE', created_at, NOW()
FROM admin_user;

-- 2. 为迁移过来的管理员赋 ADMIN 角色
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM user u
JOIN role r ON r.name = 'ADMIN'
WHERE u.username IN (SELECT username FROM admin_user);

-- 3. 历史文章归属到第一个 ADMIN
UPDATE post SET author_id = (
  SELECT u.id FROM user u
  JOIN user_role ur ON ur.user_id = u.id
  JOIN role r ON r.id = ur.role_id
  WHERE r.name = 'ADMIN' LIMIT 1
) WHERE author_id IS NULL OR author_id = 0;
```

---

## 二、领域层（Domain）

### 2.1 新增聚合根 `User`

```
bytedepth-domain/domain/user/
  User.java            -- 聚合根
  UserStatus.java      -- 枚举：PENDING / ACTIVE / BANNED
  UserRepository.java  -- 仓储接口
```

`User` 工厂方法：
- `User.register(username, passwordHash)` → status=PENDING
- `User.reconstruct(id, username, ...)` → 从持久层重建

`User` 状态流转：
- `activate()` — PENDING → ACTIVE（管理员审核通过）
- `ban()` — ACTIVE → BANNED（管理员封禁）

> 拒绝注册：PENDING 用户被拒绝时直接删除记录，无需 `reject()` 方法，也不引入 REJECTED 状态——避免垃圾数据堆积。

`UserRepository` 接口：
- `findById(Long id)`
- `findByUsername(String username)`
- `existsByUsername(String username)`
- `findByStatus(UserStatus status)`
- `save(User user)`

### 2.2 修改 `Post`

新增字段：`authorId`（文章归属）、`featured`（首页推荐）

新增工厂方法参数：`Post.create(title, content, authorId)`

新增行为：
- `feature()` / `unfeature()` — 设置/取消首页推荐
- `isOwnedBy(Long userId)` — 业务层权限校验辅助

### 2.3 修改 `Comment`

字段变更：移除 `authorName` / `authorEmail`，新增 `authorId`

工厂方法：`Comment.create(postId, authorId, content)` — status 直接为 APPROVED

移除 `approve()` / `reject()` 方法（评论不再走审核流）

> **原则**：领域层不引入 RBAC 概念，`Role` / `Permission` 是基础设施关注点。领域层只关心 `User` 的状态流转。

---

## 三、应用层（App）

### 3.1 新增 `user` 包

| 类 | 职责 |
|---|---|
| `RegisterUserCmdExe` | 检查用户名唯一性 → BCrypt 加密 → User.register → save |
| `ActivateUserCmdExe` | findById → user.activate() → 赋 USER 角色 → save |
| `BanUserCmdExe` | findById → user.ban() → save |
| `ListPendingUsersQryExe` | 查询 status=PENDING 的用户列表 |
| `GetUserProfileQryExe` | 用户基本信息 + 已发布文章分页 + 专栏列表 |
| `UserDTO` | 通用用户 DTO |
| `UserProfileDTO` | 主页 DTO（含文章列表、专栏列表） |

### 3.2 修改 `post` 包

- `CreatePostCmd` 新增 `authorId` 字段
- `ListPostsQryExe` 新增按 `authorId` 筛选（用于用户主页）
- 新增 `FeaturePostCmdExe`：调用 `post.feature()` / `post.unfeature()`
- `UpdatePostCmdExe` / `DeletePostCmdExe`：非 ADMIN 用户需校验 `post.isOwnedBy(currentUserId)`，否则抛 `DomainException`

### 3.3 修改 `comment` 包

- `SubmitCommentCmdExe.execute(postId, authorId, content)` — 移除匿名参数
- 移除 `ReviewCommentCmdExe`（评论无需审核）

---

## 四、基础设施层（Infrastructure）

### 4.1 新增 `user` 包

```
infrastructure/user/
  UserDO.java              -- user 表映射
  RoleDO.java              -- role 表映射
  PermissionDO.java        -- permission 表映射
  UserMapper.java
  RoleMapper.java
  PermissionMapper.java
  UserRepositoryImpl.java
```

### 4.2 新增 `SiteUserDetailsService`（替换 `AdminUserDetailsService`）

登录时从 DB 动态加载用户权限，是 RBAC 的核心接入点：

```
loadUserByUsername(username):
  1. 查 user 表 → 不存在抛 UsernameNotFoundException
  2. status=PENDING → 抛 DisabledException("账号待审核")
  3. status=BANNED  → 抛 LockedException("账号已封禁")
  4. 查 user_role + role_permission → 得到权限 code 列表
  5. 将 code 转为 SimpleGrantedAuthority
  6. 返回 UserDetails（含完整 GrantedAuthority 列表）
```

### 4.3 修改 `post` 包

- `PostDO` 新增 `authorId`、`featured` 字段
- `PostMapper` 新增 `selectPublishedByAuthorId(authorId, IPage)`
- `PostRepositoryImpl` 新增 `findPublishedByAuthorId(authorId, page, size)`

### 4.4 修改 `comment` 包

- `CommentDO` 移除 `authorName`/`authorEmail`，新增 `authorId`
- `CommentRepositoryImpl` 移除 PENDING 状态相关查询

---

## 五、适配层（Adapter）

### 5.1 SecurityConfig 变更

```java
@EnableMethodSecurity   // 开启 @PreAuthorize
public class SecurityConfig {
    // URL 规则仅控制"是否需要登录"
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(POST, "/posts/*/comments").authenticated()
        .requestMatchers("/posts/new", "/posts/*/edit").authenticated()
        .requestMatchers("/series/new", "/series/*/edit").authenticated()
        .anyRequest().permitAll()
    )
    // 普通用户登录后回首页（区别于原来的 /admin）
    .formLogin(form -> form
        .loginPage("/login")
        .defaultSuccessUrl("/", true)
    )
    // 权限不足 → /403，未认证 → /login
    .exceptionHandling(...)
}
```

`/admin/**` 不再在 SecurityConfig 配置，改为每个 Admin Controller 方法上的 `@PreAuthorize`。

### 5.2 新增 Controller

| Controller | 路由 | 关键权限 |
|---|---|---|
| `RegisterController` | `GET/POST /register` | permitAll |
| `UserProfileController` | `GET /u/{username}` | permitAll |
| `AdminUserController` | `GET /admin/users` | `system:user:approve` |
| | `POST /admin/users/{id}/activate` | `system:user:approve` |
| | `DELETE /admin/users/{id}` | `system:user:approve`（拒绝 PENDING 用户，删除记录） |
| | `POST /admin/users/{id}/ban` | `system:user:manage`（封禁 ACTIVE 用户） |

### 5.3 修改 Controller

**`CommentController`**：
- 移除 `authorName`/`authorEmail` 参数
- 从 `@AuthenticationPrincipal` 获取当前用户 id
- 加 `@PreAuthorize("hasAuthority('blog:comment:create')")`

**`AdminCommentController`**：
- 移除 `approve`/`reject` 方法
- 所有方法加 `@PreAuthorize("hasAuthority('blog:comment:manage')")`

**`AdminPostController`**：
- 新增 `POST /{id}/feature` → `@PreAuthorize("hasAuthority('blog:post:feature')")`
- 所有现有方法加对应 `@PreAuthorize`

### 5.4 模板变更（Thymeleaf）

| 模板 | 变更 |
|---|---|
| `public/register.html` | **新增** — 用户名 + 密码注册表单 |
| `public/profile.html` | **新增** — `/u/{username}` 用户主页 |
| `admin/users/list.html` | **新增** — 待审核用户列表，通过/封禁按钮 |
| `public/login.html` | 新增"还没有账号？去注册"链接 |
| `public/post-detail.html` | 评论区：未登录显示"登录后评论"提示；移除姓名/邮箱输入框 |
| `fragments/navbar.html` | 已登录：用户名 + 个人主页 + 退出；未登录：登录/注册按钮 |
| `admin/comments/list.html` | 移除审核按钮 |

---

## 六、注册审核流程总结

```
用户填写用户名+密码 → POST /register
  → RegisterUserCmdExe → status=PENDING → 跳转 /login?registered=1
                              ↓
                    管理员登录后台 /admin/users
                    看到待审核列表
                              ↓
                    点击"通过" → ActivateUserCmdExe
                      → status=ACTIVE + 赋 USER 角色
                      → 用户下次登录即可正常使用

                    点击"拒绝" → 直接删除 PENDING 记录（无需 REJECTED 状态）
                    点击"封禁" → BanUserCmdExe → status=BANNED（针对已 ACTIVE 用户）
```

---

## 七、扩展性说明

- 新模块（论坛等）只需在 `permission` 表插入新权限项（如 `forum:post:create`），在角色中绑定，无需改代码
- `UserDetailsService` 动态加载权限，新权限立即生效
- `role_permission` 可通过后台管理界面（`system:role:manage` 权限）动态调整，无需重启服务
