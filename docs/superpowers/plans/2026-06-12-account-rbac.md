# 账户系统与 RBAC 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 bytedepth 博客平台新增多用户账户体系（注册/登录/审核）、动态 RBAC 权限控制、文章/评论归属，以及用户个人主页。

**Architecture:** DDD 四层（domain → app → infra → adapter），Spring Security 6 + `@PreAuthorize` 方法级权限，动态 RBAC 三表（role/permission/role_permission），Flyway 迁移，Testcontainers 集成测试。

**Tech Stack:** Spring Boot 3, Spring Security 6, MyBatis Plus 3, Flyway, Thymeleaf, JUnit 5, Mockito, Testcontainers (MySQL)

---

## 文件清单

### 新建
| 文件 | 说明 |
|---|---|
| `bytedepth-start/src/main/resources/db/migration/V10__account_rbac.sql` | Flyway V10 迁移 |
| `bytedepth-domain/.../domain/user/User.java` | 用户聚合根 |
| `bytedepth-domain/.../domain/user/UserStatus.java` | 枚举 PENDING/ACTIVE/BANNED |
| `bytedepth-domain/.../domain/user/UserRepository.java` | 仓储接口 |
| `bytedepth-domain/.../domain/user/UserTest.java` | 领域单测 |
| `bytedepth-app/.../app/user/UserDTO.java` | 通用 DTO |
| `bytedepth-app/.../app/user/UserProfileDTO.java` | 个人主页 DTO |
| `bytedepth-app/.../app/user/RegisterUserCmdExe.java` | 注册命令 |
| `bytedepth-app/.../app/user/ActivateUserCmdExe.java` | 审核通过命令 |
| `bytedepth-app/.../app/user/BanUserCmdExe.java` | 封禁命令 |
| `bytedepth-app/.../app/user/ListPendingUsersQryExe.java` | 待审核列表查询 |
| `bytedepth-app/.../app/user/GetUserProfileQryExe.java` | 个人主页查询 |
| `bytedepth-app/.../app/post/command/FeaturePostCmdExe.java` | 首页推荐命令 |
| `bytedepth-infrastructure/.../infrastructure/user/UserDO.java` | user 表 DO |
| `bytedepth-infrastructure/.../infrastructure/user/RoleDO.java` | role 表 DO |
| `bytedepth-infrastructure/.../infrastructure/user/UserRoleDO.java` | user_role 表 DO |
| `bytedepth-infrastructure/.../infrastructure/user/UserMapper.java` | MyBatis Mapper |
| `bytedepth-infrastructure/.../infrastructure/user/RoleMapper.java` | Role Mapper |
| `bytedepth-infrastructure/.../infrastructure/user/UserRoleMapper.java` | UserRole Mapper |
| `bytedepth-infrastructure/.../infrastructure/user/UserRepositoryImpl.java` | 仓储实现 |
| `bytedepth-infrastructure/.../infrastructure/user/SiteUserDetails.java` | 自定义 UserDetails（含 id） |
| `bytedepth-infrastructure/.../infrastructure/user/SiteUserDetailsService.java` | 动态加载权限 |
| `bytedepth-adapter/.../adapter/web/portal/RegisterController.java` | 注册 Controller |
| `bytedepth-adapter/.../adapter/web/portal/UserProfileController.java` | 个人主页 Controller |
| `bytedepth-adapter/.../adapter/web/admin/AdminUserController.java` | 用户审核 Controller |
| `bytedepth-start/src/main/resources/templates/public/register.html` | 注册页 |
| `bytedepth-start/src/main/resources/templates/public/profile.html` | 个人主页 |
| `bytedepth-start/src/main/resources/templates/admin/users/list.html` | 待审核用户列表 |
| `bytedepth-start/src/test/java/.../AccountFlowE2ETest.java` | E2E 测试 |

### 修改
| 文件 | 变更 |
|---|---|
| `domain/post/Post.java` | 新增 authorId、featured、isOwnedBy()、feature()、unfeature() |
| `domain/post/PostRepository.java` | 新增 findPublishedByAuthorId、countPublishedByAuthorId |
| `domain/comment/Comment.java` | authorId+authorName 替换匿名字段，create() 直接 APPROVED |
| `app/comment/CommentDTO.java` | 新增 authorId，移除（不存在的）authorEmail |
| `app/comment/SubmitCommentCmdExe.java` | 签名改为 (postId, authorId, authorName, content) |
| `app/comment/ListCommentsQryExe.java` | findPending() 改为 findAll(page,size) 供管理用 |
| `app/post/command/CreatePostCmd.java` | 新增 authorId 字段 |
| `app/post/command/CreatePostCmdExe.java` | 传 authorId 给 Post.create() |
| `app/post/query/PostDTO.java` | 新增 authorId |
| `infrastructure/post/PostDO.java` | 新增 authorId、featured |
| `infrastructure/post/PostRepositoryImpl.java` | 更新 toDO/toEntity；新增 findPublishedByAuthorId |
| `infrastructure/comment/CommentDO.java` | 新增 authorId，移除 authorEmail |
| `infrastructure/comment/CommentRepositoryImpl.java` | 移除 PENDING 查询，更新 toDO/toEntity |
| `infrastructure/admin/AdminUserDetailsService.java` | **删除**，由 SiteUserDetailsService 替代 |
| `adapter/web/security/SecurityConfig.java` | 加 @EnableMethodSecurity，简化 URL 规则 |
| `adapter/web/portal/PostController.java` | 从 SecurityContext 取 authorId；publish 加所有权校验 |
| `adapter/web/portal/CommentController.java` | 改为认证用户；加 @PreAuthorize |
| `adapter/web/admin/AdminCommentController.java` | 移除 approve/reject；改为列出全部评论 |
| `adapter/web/admin/AdminPostController.java` | 加 feature 端点；所有方法加 @PreAuthorize |
| `templates/fragments/nav.html` | 登录/注册/用户名/退出 |
| `templates/public/posts/detail.html` | 评论区：登录提示 / 已登录表单 |
| `templates/public/login.html` | 加注册链接；改标题为通用登录 |
| `templates/admin/comments/list.html` | 移除审核按钮；改为评论列表 |

---

## Task 1: Flyway V10 数据库迁移

**Files:**
- Create: `bytedepth-start/src/main/resources/db/migration/V10__account_rbac.sql`

- [ ] **Step 1: 启动本地 MySQL（若未运行）**

```bash
docker run -d --name bytedepth-mysql \
  -e MYSQL_ROOT_PASSWORD= -e MYSQL_DATABASE=bytedepth \
  -p 3306:3306 mysql:8.0 2>/dev/null || echo "already running"
# 等待就绪
sleep 5 && mysql -u root -e "SELECT 1" bytedepth 2>/dev/null && echo "MySQL ready"
```

- [ ] **Step 2: 创建 V10 迁移文件**

`bytedepth-start/src/main/resources/db/migration/V10__account_rbac.sql`:

```sql
-- =====================================================
-- V10: 账户系统 + RBAC 权限表
-- =====================================================

-- 1. 统一用户表（替换 admin_user）
CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT       AUTO_INCREMENT PRIMARY KEY,
  `username`   VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
  `password`   VARCHAR(100) NOT NULL       COMMENT 'BCrypt 哈希',
  `email`      VARCHAR(100),
  `avatar`     VARCHAR(255),
  `bio`        TEXT,
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/BANNED',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号';

-- 2. 角色表
CREATE TABLE IF NOT EXISTS `role` (
  `id`          BIGINT      AUTO_INCREMENT PRIMARY KEY,
  `name`        VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名：ADMIN / USER',
  `description` VARCHAR(255),
  `created_at`  DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 权限表
CREATE TABLE IF NOT EXISTS `permission` (
  `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  `code`        VARCHAR(100) NOT NULL UNIQUE COMMENT '如 blog:post:create',
  `description` VARCHAR(255),
  `module`      VARCHAR(50)  NOT NULL COMMENT 'blog/project/system/admin',
  `created_at`  DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 角色-权限关联
CREATE TABLE IF NOT EXISTS `role_permission` (
  `role_id`       BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 用户-角色关联
CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. post 表新增 author_id 和 featured
ALTER TABLE `post`
  ADD COLUMN `author_id` BIGINT NULL    AFTER `id`,
  ADD COLUMN `featured`  TINYINT(1) NOT NULL DEFAULT 0 AFTER `status`;

-- 7. comment 表：移除 author_email，新增 author_id
ALTER TABLE `comment`
  ADD COLUMN `author_id` BIGINT NULL AFTER `post_id`,
  DROP COLUMN `author_email`;

-- 8. 删除旧的 PENDING 匿名评论（无法迁移）
DELETE FROM `comment` WHERE `status` = 'PENDING';

-- 9. 种子：角色
INSERT IGNORE INTO `role` (`name`, `description`, `created_at`) VALUES
  ('ADMIN', '管理员，拥有全部权限', NOW()),
  ('USER',  '注册成员', NOW());

-- 10. 种子：权限
INSERT IGNORE INTO `permission` (`code`, `description`, `module`, `created_at`) VALUES
  ('blog:post:create',       '创建文章',       'blog',    NOW()),
  ('blog:post:edit:own',     '编辑自己的文章',  'blog',    NOW()),
  ('blog:post:delete:own',   '删除自己的文章',  'blog',    NOW()),
  ('blog:post:publish:own',  '发布自己的文章',  'blog',    NOW()),
  ('blog:post:feature',      '设为首页推荐',    'blog',    NOW()),
  ('blog:post:manage',       '管理任意文章',    'blog',    NOW()),
  ('blog:comment:create',    '发表评论',        'blog',    NOW()),
  ('blog:comment:manage',    '管理任意评论',    'blog',    NOW()),
  ('blog:series:create:own', '创建专栏',        'blog',    NOW()),
  ('blog:series:edit:own',   '编辑自己的专栏',  'blog',    NOW()),
  ('blog:series:manage',     '管理任意专栏',    'blog',    NOW()),
  ('blog:category:manage',   '管理分类',        'blog',    NOW()),
  ('blog:tag:manage',        '管理标签',        'blog',    NOW()),
  ('project:manage',         '管理项目',        'project', NOW()),
  ('system:user:approve',    '审核用户注册',    'system',  NOW()),
  ('system:user:manage',     '管理用户',        'system',  NOW()),
  ('system:role:manage',     '管理角色与权限',  'system',  NOW()),
  ('admin:dashboard:view',   '访问后台仪表盘',  'admin',   NOW());

-- 11. ADMIN 角色拥有全部权限
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
  SELECT r.id, p.id FROM `role` r, `permission` p WHERE r.name = 'ADMIN';

-- 12. USER 角色权限（常规成员）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
  SELECT r.id, p.id FROM `role` r
  JOIN `permission` p ON p.code IN (
    'blog:post:create', 'blog:post:edit:own', 'blog:post:delete:own',
    'blog:post:publish:own', 'blog:comment:create',
    'blog:series:create:own', 'blog:series:edit:own'
  )
  WHERE r.name = 'USER';

-- 13. 迁移 admin_user → user（状态 ACTIVE）
INSERT IGNORE INTO `user` (`username`, `password`, `status`, `created_at`, `updated_at`)
  SELECT `username`, `password`, 'ACTIVE', `created_at`, NOW() FROM `admin_user`;

-- 14. 为迁移的管理员账号赋 ADMIN 角色
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
  SELECT u.id, r.id
  FROM `user` u
  JOIN `admin_user` au ON au.username = u.username
  JOIN `role` r ON r.name = 'ADMIN';

-- 15. 历史文章归属到第一个 ADMIN 用户
UPDATE `post` SET `author_id` = (
  SELECT u.id FROM `user` u
  JOIN `user_role` ur ON ur.user_id = u.id
  JOIN `role` ro ON ro.id = ur.role_id AND ro.name = 'ADMIN'
  ORDER BY u.id LIMIT 1
) WHERE `author_id` IS NULL;

-- 16. 设置 author_id NOT NULL
ALTER TABLE `post` MODIFY COLUMN `author_id` BIGINT NOT NULL;
```

- [ ] **Step 3: 运行迁移验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS（Flyway 自动执行 V10）
```

- [ ] **Step 4: Commit**

```bash
git add bytedepth-start/src/main/resources/db/migration/V10__account_rbac.sql
git commit -m "chore: V10 迁移——RBAC 表、user 表、post/comment 字段变更"
```

---

## Task 2: User 领域聚合根（TDD）

**Files:**
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/user/UserStatus.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/user/UserRepository.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/user/User.java`
- Test: `bytedepth-domain/src/test/java/manfred/bytedepth/domain/user/UserTest.java`

- [ ] **Step 1: 写失败测试**

```java
// bytedepth-domain/src/test/java/manfred/bytedepth/domain/user/UserTest.java
package manfred.bytedepth.domain.user;

import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void register_setsUsernameAndStatusPending() {
        User user = User.register("alice", "$2a$10$hash");
        assertEquals("alice", user.getUsername());
        assertEquals(UserStatus.PENDING, user.getStatus());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void activate_pendingUser_becomesActive() {
        User user = User.register("alice", "hash");
        user.activate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void activate_alreadyActive_throwsDomainException() {
        User user = User.register("alice", "hash");
        user.activate();
        assertThrows(DomainException.class, user::activate);
    }

    @Test
    void ban_activeUser_becomesBanned() {
        User user = User.register("alice", "hash");
        user.activate();
        user.ban();
        assertEquals(UserStatus.BANNED, user.getStatus());
    }

    @Test
    void ban_alreadyBanned_throwsDomainException() {
        User user = User.register("alice", "hash");
        user.activate();
        user.ban();
        assertThrows(DomainException.class, user::ban);
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-domain \
  -Dtest=UserTest -q 2>&1 | tail -5
# 期望：FAIL — cannot find symbol User
```

- [ ] **Step 3: 创建 UserStatus、UserRepository、User**

```java
// UserStatus.java
package manfred.bytedepth.domain.user;
public enum UserStatus { PENDING, ACTIVE, BANNED }
```

```java
// UserRepository.java
package manfred.bytedepth.domain.user;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByStatus(UserStatus status);
    User save(User user);
    void deleteById(Long id);
    void assignRole(Long userId, String roleName); // 赋角色，实现在 infra 层
}
```

```java
// User.java
package manfred.bytedepth.domain.user;

import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;
import java.time.LocalDateTime;

@Getter
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String avatar;
    private String bio;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private User() {}

    public static User register(String username, String passwordHash) {
        User u = new User();
        u.username = username;
        u.passwordHash = passwordHash;
        u.status = UserStatus.PENDING;
        u.createdAt = LocalDateTime.now();
        u.updatedAt = LocalDateTime.now();
        return u;
    }

    public static User reconstruct(Long id, String username, String passwordHash,
                                   String email, String avatar, String bio,
                                   UserStatus status,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        User u = new User();
        u.id = id; u.username = username; u.passwordHash = passwordHash;
        u.email = email; u.avatar = avatar; u.bio = bio;
        u.status = status; u.createdAt = createdAt; u.updatedAt = updatedAt;
        return u;
    }

    public void activate() {
        if (this.status != UserStatus.PENDING) {
            throw new DomainException("只有待审核账号可激活，当前状态：" + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void ban() {
        if (this.status == UserStatus.BANNED) {
            throw new DomainException("账号已被封禁");
        }
        this.status = UserStatus.BANNED;
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-domain \
  -Dtest=UserTest -q
# 期望：BUILD SUCCESS，5 tests passed
```

- [ ] **Step 5: Commit**

```bash
git add bytedepth-domain/src/main/java/manfred/bytedepth/domain/user/
git add bytedepth-domain/src/test/java/manfred/bytedepth/domain/user/
git commit -m "feat: User 领域聚合根（register/activate/ban，TDD）"
```

---

## Task 3: 修改 Post 领域（TDD）

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/Post.java`
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java`
- Modify: `bytedepth-domain/src/test/java/manfred/bytedepth/domain/post/PostTest.java`

- [ ] **Step 1: 在 PostTest 中追加失败测试**

在 `PostTest.java` 文件末尾追加：

```java
@Test
void create_withAuthorId_setsAuthorIdAndFeaturedFalse() {
    Post post = Post.create("Title", "Content", 42L);
    assertEquals(42L, post.getAuthorId());
    assertFalse(post.getFeatured());
}

@Test
void isOwnedBy_sameId_returnsTrue() {
    Post post = Post.create("T", "C", 5L);
    assertTrue(post.isOwnedBy(5L));
}

@Test
void isOwnedBy_differentId_returnsFalse() {
    Post post = Post.create("T", "C", 5L);
    assertFalse(post.isOwnedBy(9L));
}

@Test
void feature_setsFeatureTrue() {
    Post post = Post.create("T", "C", 1L);
    post.feature();
    assertTrue(post.getFeatured());
}

@Test
void unfeature_setsFeaturedFalse() {
    Post post = Post.create("T", "C", 1L);
    post.feature();
    post.unfeature();
    assertFalse(post.getFeatured());
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-domain \
  -Dtest=PostTest -q 2>&1 | tail -5
# 期望：FAIL — cannot find symbol getAuthorId()
```

- [ ] **Step 3: 更新 Post.java**

在 `Post.java` 中：
1. 新增字段 `private Long authorId;` 和 `private Boolean featured = false;`
2. 新增 `create()` 重载（含 authorId）
3. 新增 `reconstruct()` 重载（含 authorId、featured）
4. 新增 `isOwnedBy()`、`feature()`、`unfeature()` 方法

```java
// 在 Post 类中新增字段：
private Long authorId;
private Boolean featured = false;

// 新增 create 重载（原有 create(title,content) 保留向后兼容，authorId 为 null）：
public static Post create(String title, String content, Long authorId) {
    Post post = new Post();
    post.title = title;
    post.content = content;
    post.authorId = authorId;
    post.status = PostStatus.DRAFT;
    post.featured = false;
    post.createdAt = LocalDateTime.now();
    post.updatedAt = LocalDateTime.now();
    return post;
}

// 新增含 authorId+featured 的 reconstruct 重载：
public static Post reconstruct(Long id, String title, String content, PostStatus status,
                               LocalDateTime createdAt, LocalDateTime publishedAt,
                               LocalDateTime updatedAt, Long categoryId,
                               Long authorId, Boolean featured) {
    Post post = reconstruct(id, title, content, status,
                            createdAt, publishedAt, updatedAt, categoryId);
    post.authorId = authorId;
    post.featured = Boolean.TRUE.equals(featured);
    return post;
}

// 新增方法：
public boolean isOwnedBy(Long userId) {
    return this.authorId != null && this.authorId.equals(userId);
}

public void feature() { this.featured = true; }
public void unfeature() { this.featured = false; }
```

- [ ] **Step 4: 在 PostRepository 接口追加新方法**

```java
// 在 PostRepository.java 接口中追加：
List<Post> findPublishedByAuthorId(Long authorId, int page, int size);
long countPublishedByAuthorId(Long authorId);
```

- [ ] **Step 5: 运行测试确认全部通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-domain \
  -Dtest=PostTest -q
# 期望：BUILD SUCCESS，全部通过（含新增 5 个）
```

- [ ] **Step 6: Commit**

```bash
git add bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/
git add bytedepth-domain/src/test/java/manfred/bytedepth/domain/post/
git commit -m "feat: Post 领域新增 authorId/featured/isOwnedBy/feature（TDD）"
```

---

## Task 4: 修改 Comment 领域（TDD）

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/comment/Comment.java`
- Modify: `bytedepth-domain/src/test/java/manfred/bytedepth/domain/comment/CommentTest.java`

> 设计决策：保留 `authorName` 作为快照（创建时从 username 复制），新增 `authorId`；评论直接 APPROVED，移除 `approve()/reject()`。

- [ ] **Step 1: 在 CommentTest 追加失败测试**

```java
@Test
void create_withAuthorId_setsAuthorIdAndApproved() {
    Comment c = Comment.create(1L, 42L, "alice", "Hello");
    assertEquals(42L, c.getAuthorId());
    assertEquals("alice", c.getAuthorName());
    assertEquals(CommentStatus.APPROVED, c.getStatus());
}

@Test
void create_withNullAuthorId_legacyMode_isAllowed() {
    // 旧评论迁移兼容：authorId 可为 null
    Comment c = Comment.create(1L, null, "Anonymous", "Old comment");
    assertNull(c.getAuthorId());
    assertEquals(CommentStatus.APPROVED, c.getStatus());
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-domain \
  -Dtest=CommentTest -q 2>&1 | tail -5
# 期望：FAIL — create 方法签名不匹配
```

- [ ] **Step 3: 更新 Comment.java**

```java
// 更新后的完整 Comment.java
package manfred.bytedepth.domain.comment;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class Comment {

    private Long id;
    private Long postId;
    private Long authorId;      // 注册用户 ID（旧评论可为 null）
    private String authorName;  // 用户名快照（显示用）
    private String content;
    private CommentStatus status;
    private LocalDateTime createdAt;

    private Comment() {}

    // 新签名：直接 APPROVED，authorId 可为 null（兼容历史数据）
    public static Comment create(Long postId, Long authorId,
                                 String authorName, String content) {
        Comment c = new Comment();
        c.postId = postId;
        c.authorId = authorId;
        c.authorName = authorName;
        c.content = content;
        c.status = CommentStatus.APPROVED;
        c.createdAt = LocalDateTime.now();
        return c;
    }

    public static Comment reconstruct(Long id, Long postId, Long authorId,
                                      String authorName, String content,
                                      CommentStatus status, LocalDateTime createdAt) {
        Comment c = new Comment();
        c.id = id; c.postId = postId; c.authorId = authorId;
        c.authorName = authorName; c.content = content;
        c.status = status; c.createdAt = createdAt;
        return c;
    }
    // approve() 和 reject() 已移除（评论不再走审核流）
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-domain \
  -Dtest=CommentTest -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add bytedepth-domain/src/main/java/manfred/bytedepth/domain/comment/Comment.java
git add bytedepth-domain/src/test/java/manfred/bytedepth/domain/comment/CommentTest.java
git commit -m "feat: Comment 领域——authorId+authorName 快照，直接 APPROVED（TDD）"
```

---

## Task 5: 用户应用层命令（TDD）

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/RegisterUserCmdExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/ActivateUserCmdExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/BanUserCmdExe.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/user/RegisterUserCmdExeTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/user/ActivateUserCmdExeTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/user/BanUserCmdExeTest.java`

- [ ] **Step 1: 写失败测试（三个文件）**

```java
// RegisterUserCmdExeTest.java
package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserCmdExeTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    private RegisterUserCmdExe exe;

    @BeforeEach
    void setUp() { exe = new RegisterUserCmdExe(userRepository, passwordEncoder); }

    @Test
    void execute_newUsername_savesUserAsPending() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("$2a$hash$");

        exe.execute("alice", "pass");

        verify(userRepository).save(argThat(u ->
            "alice".equals(u.getUsername()) && u.getStatus() == UserStatus.PENDING));
    }

    @Test
    void execute_duplicateUsername_throwsAndDoesNotSave() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(DomainException.class, () -> exe.execute("alice", "pass"));
        verify(userRepository, never()).save(any());
    }
}
```

```java
// ActivateUserCmdExeTest.java
package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivateUserCmdExeTest {

    @Mock private UserRepository userRepository;
    private ActivateUserCmdExe exe;

    @BeforeEach
    void setUp() { exe = new ActivateUserCmdExe(userRepository); }

    @Test
    void execute_pendingUser_savesActiveAndAssignsRole() {
        User user = User.register("bob", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        exe.execute(1L);

        verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.ACTIVE));
        verify(userRepository).assignRole(1L, "USER");
    }

    @Test
    void execute_userNotFound_throwsDomainException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> exe.execute(99L));
    }
}
```

```java
// BanUserCmdExeTest.java
package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanUserCmdExeTest {

    @Mock private UserRepository userRepository;
    private BanUserCmdExe exe;

    @BeforeEach
    void setUp() { exe = new BanUserCmdExe(userRepository); }

    @Test
    void execute_activeUser_savesBanned() {
        User user = User.register("carol", "hash");
        user.activate();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        exe.execute(2L);

        verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.BANNED));
    }

    @Test
    void execute_userNotFound_throwsDomainException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> exe.execute(99L));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest="RegisterUserCmdExeTest,ActivateUserCmdExeTest,BanUserCmdExeTest" -q 2>&1 | tail -5
# 期望：FAIL — class not found
```

- [ ] **Step 3: 实现三个命令类**

```java
// RegisterUserCmdExe.java
package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterUserCmdExe {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void execute(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new DomainException("用户名已存在：" + username);
        }
        User user = User.register(username, passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }
}
```

```java
// ActivateUserCmdExe.java
package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivateUserCmdExe {

    private final UserRepository userRepository;

    public void execute(Long userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException("用户不存在：" + userId));
        user.activate();
        userRepository.save(user);
        userRepository.assignRole(userId, "USER");
    }
}
```

```java
// BanUserCmdExe.java
package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BanUserCmdExe {

    private final UserRepository userRepository;

    public void execute(Long userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException("用户不存在：" + userId));
        user.ban();
        userRepository.save(user);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest="RegisterUserCmdExeTest,ActivateUserCmdExeTest,BanUserCmdExeTest" -q
# 期望：BUILD SUCCESS，6 tests passed
```

- [ ] **Step 5: Commit**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/user/
git add bytedepth-app/src/test/java/manfred/bytedepth/app/user/
git commit -m "feat: 用户注册/激活/封禁应用层命令（TDD）"
```

---

## Task 6: 用户应用层查询与 DTO（TDD）

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/UserDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/UserProfileDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/ListPendingUsersQryExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/GetUserProfileQryExe.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/user/ListPendingUsersQryExeTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/user/GetUserProfileQryExeTest.java`

- [ ] **Step 1: 写失败测试**

```java
// ListPendingUsersQryExeTest.java
package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListPendingUsersQryExeTest {

    @Mock private UserRepository userRepository;
    private ListPendingUsersQryExe exe;

    @BeforeEach
    void setUp() { exe = new ListPendingUsersQryExe(userRepository); }

    @Test
    void execute_returnsPendingUsers() {
        User pending = User.reconstruct(1L, "dave", "hash", null, null, null,
                UserStatus.PENDING, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByStatus(UserStatus.PENDING)).thenReturn(List.of(pending));

        List<UserDTO> result = exe.execute();

        assertEquals(1, result.size());
        assertEquals("dave", result.get(0).getUsername());
        assertEquals("PENDING", result.get(0).getStatus());
    }
}
```

```java
// GetUserProfileQryExeTest.java
package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserProfileQryExeTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    private GetUserProfileQryExe exe;

    @BeforeEach
    void setUp() { exe = new GetUserProfileQryExe(userRepository, postRepository); }

    @Test
    void execute_existingUser_returnsProfile() {
        User user = User.reconstruct(1L, "alice", "hash", null, null, "Bio",
                UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(postRepository.findPublishedByAuthorId(1L, 1, 10)).thenReturn(List.of());
        when(postRepository.countPublishedByAuthorId(1L)).thenReturn(0L);

        UserProfileDTO profile = exe.execute("alice");

        assertEquals("alice", profile.getUsername());
        assertEquals("Bio", profile.getBio());
        assertEquals(0, profile.getPostCount());
    }

    @Test
    void execute_unknownUser_throwsDomainException() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> exe.execute("nobody"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest="ListPendingUsersQryExeTest,GetUserProfileQryExeTest" -q 2>&1 | tail -5
# 期望：FAIL
```

- [ ] **Step 3: 创建 DTO 和查询类**

```java
// UserDTO.java
package manfred.bytedepth.app.user;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String status;
    private LocalDateTime createdAt;
}
```

```java
// UserProfileDTO.java
package manfred.bytedepth.app.user;

import lombok.Data;
import manfred.bytedepth.app.post.query.PostDTO;
import java.util.List;

@Data
public class UserProfileDTO {
    private Long id;
    private String username;
    private String bio;
    private String avatar;
    private int postCount;
    private List<PostDTO> recentPosts;
}
```

```java
// ListPendingUsersQryExe.java
package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListPendingUsersQryExe {

    private final UserRepository userRepository;

    public List<UserDTO> execute() {
        return userRepository.findByStatus(UserStatus.PENDING).stream()
            .map(u -> {
                UserDTO dto = new UserDTO();
                dto.setId(u.getId());
                dto.setUsername(u.getUsername());
                dto.setStatus(u.getStatus().name());
                dto.setCreatedAt(u.getCreatedAt());
                return dto;
            })
            .collect(Collectors.toList());
    }
}
```

```java
// GetUserProfileQryExe.java
package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserProfileQryExe {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public UserProfileDTO execute(String username) {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new DomainException("用户不存在：" + username));
        List<Post> posts = postRepository.findPublishedByAuthorId(user.getId(), 1, 10);
        long count = postRepository.countPublishedByAuthorId(user.getId());

        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setBio(user.getBio());
        dto.setAvatar(user.getAvatar());
        dto.setPostCount((int) count);
        dto.setRecentPosts(posts.stream()
            .map(p -> {
                PostDTO pdto = new PostDTO();
                pdto.setId(p.getId());
                pdto.setTitle(p.getTitle());
                pdto.setPublishedAt(p.getPublishedAt());
                pdto.setAuthorId(p.getAuthorId());
                return pdto;
            })
            .collect(Collectors.toList()));
        return dto;
    }
}
```

- [ ] **Step 4: 确认测试通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest="ListPendingUsersQryExeTest,GetUserProfileQryExeTest" -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/user/
git add bytedepth-app/src/test/java/manfred/bytedepth/app/user/
git commit -m "feat: 用户查询层（ListPending/GetUserProfile/DTO，TDD）"
```

---

## Task 7: 修改 Post 应用层（TDD）

**Files:**
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/command/CreatePostCmd.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/command/CreatePostCmdExe.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/command/FeaturePostCmdExe.java`
- Modify: `bytedepth-app/src/test/java/manfred/bytedepth/app/post/command/CreatePostCmdExeTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/post/command/FeaturePostCmdExeTest.java`

- [ ] **Step 1: 更新 CreatePostCmdExeTest（追加对 authorId 的校验）**

在 `CreatePostCmdExeTest.java` 中更新两个已有测试方法，使用包含 `authorId` 的 `cmd`：

```java
// 在已有测试类中把 cmd 构造改为：
CreatePostCmd cmd = new CreatePostCmd();
cmd.setTitle("标题");
cmd.setContent("内容");
cmd.setAuthorId(1L);   // ← 新增
```

并追加新测试：

```java
@Test
void execute_passesAuthorIdToPost() {
    Post savedPost = Post.reconstruct(3L, "T", "C", PostStatus.DRAFT,
            LocalDateTime.now(), null, LocalDateTime.now(), null, 7L, false);
    when(postRepository.save(any(Post.class))).thenReturn(savedPost);

    CreatePostCmd cmd = new CreatePostCmd();
    cmd.setTitle("T"); cmd.setContent("C"); cmd.setAuthorId(7L);

    Long id = createPostCmdExe.execute(cmd);

    assertEquals(3L, id);
    verify(postRepository).save(argThat(p -> Long.valueOf(7L).equals(p.getAuthorId())));
}
```

- [ ] **Step 2: 写 FeaturePostCmdExeTest**

```java
// FeaturePostCmdExeTest.java
package manfred.bytedepth.app.post.command;

import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeaturePostCmdExeTest {

    @Mock private PostRepository postRepository;
    private FeaturePostCmdExe exe;

    @BeforeEach
    void setUp() { exe = new FeaturePostCmdExe(postRepository); }

    @Test
    void feature_setsFeatureTrue() {
        Post post = Post.reconstruct(1L, "T", "C", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, 1L, false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        exe.feature(1L);

        verify(postRepository).save(argThat(p -> Boolean.TRUE.equals(p.getFeatured())));
    }

    @Test
    void unfeature_setsFeaturedFalse() {
        Post post = Post.reconstruct(1L, "T", "C", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, 1L, true);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        exe.unfeature(1L);

        verify(postRepository).save(argThat(p -> !Boolean.TRUE.equals(p.getFeatured())));
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest="CreatePostCmdExeTest,FeaturePostCmdExeTest" -q 2>&1 | tail -5
# 期望：FAIL
```

- [ ] **Step 4: 实现变更**

`CreatePostCmd.java` — 新增 `authorId` 字段：

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePostCmd extends Command {
    private String title;
    private String content;
    private Long categoryId;
    private Long authorId;   // NEW
}
```

`CreatePostCmdExe.java` — 传 `authorId`：

```java
public Long execute(CreatePostCmd cmd) {
    Post post = Post.create(cmd.getTitle(), cmd.getContent(), cmd.getAuthorId());
    if (cmd.getCategoryId() != null) {
        post.assignCategory(cmd.getCategoryId());
    }
    return postRepository.save(post).getId();
}
```

`PostDTO.java` — 新增字段（在已有字段后追加）：

```java
private Long authorId;   // NEW
```

`FeaturePostCmdExe.java` — 新建：

```java
package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeaturePostCmdExe {

    private final PostRepository postRepository;

    public void feature(Long postId) {
        var post = postRepository.findById(postId)
            .orElseThrow(() -> new DomainException("文章不存在：" + postId));
        post.feature();
        postRepository.save(post);
    }

    public void unfeature(Long postId) {
        var post = postRepository.findById(postId)
            .orElseThrow(() -> new DomainException("文章不存在：" + postId));
        post.unfeature();
        postRepository.save(post);
    }
}
```

- [ ] **Step 5: 全量编译确认**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 6: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest="CreatePostCmdExeTest,FeaturePostCmdExeTest" -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 7: Commit**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/post/
git add bytedepth-app/src/test/java/manfred/bytedepth/app/post/
git commit -m "feat: Post 应用层——CreatePostCmd 加 authorId，新增 FeaturePostCmdExe（TDD）"
```

---

## Task 8: 修改 Comment 应用层（TDD）

**Files:**
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/comment/SubmitCommentCmdExe.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/comment/CommentDTO.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/comment/ListCommentsQryExe.java`

- [ ] **Step 1: 更新 SubmitCommentCmdExe 测试**

在已有测试文件（若存在）或新建 `SubmitCommentCmdExeTest.java`：

```java
package manfred.bytedepth.app.comment;

import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitCommentCmdExeTest {

    @Mock private CommentRepository commentRepository;
    private SubmitCommentCmdExe exe;

    @BeforeEach
    void setUp() { exe = new SubmitCommentCmdExe(commentRepository); }

    @Test
    void execute_savesCommentWithAuthorIdAndNameSnapshot() {
        exe.execute(10L, 42L, "alice", "Great post!");

        verify(commentRepository).save(argThat(c ->
            Long.valueOf(42L).equals(c.getAuthorId())
            && "alice".equals(c.getAuthorName())
            && "Great post!".equals(c.getContent())
        ));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest=SubmitCommentCmdExeTest -q 2>&1 | tail -5
# 期望：FAIL（签名不匹配）
```

- [ ] **Step 3: 更新 SubmitCommentCmdExe**

```java
package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmitCommentCmdExe {

    private final CommentRepository commentRepository;

    public void execute(Long postId, Long authorId, String authorName, String content) {
        Comment comment = Comment.create(postId, authorId, authorName, content);
        commentRepository.save(comment);
    }
}
```

- [ ] **Step 4: 更新 CommentDTO**

```java
@Data
public class CommentDTO {
    private Long id;
    private Long postId;
    private Long authorId;   // NEW
    private String authorName;
    // authorEmail 已移除
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 更新 ListCommentsQryExe**

将 `findPending()` 改为 `findAll(page, size)` 供管理员用（Portal 侧 `findApprovedByPostId` 不变）：

```java
// 在 ListCommentsQryExe 中：
// 原 findPending() 方法重命名为 findAll() 返回全部评论（管理员使用）
public List<CommentDTO> findAll(int page, int size) {
    return commentRepository.findAll(page, size).stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
}

// toDTO 方法更新：
private CommentDTO toDTO(Comment c) {
    CommentDTO dto = new CommentDTO();
    dto.setId(c.getId());
    dto.setPostId(c.getPostId());
    dto.setAuthorId(c.getAuthorId());
    dto.setAuthorName(c.getAuthorName());
    dto.setContent(c.getContent());
    dto.setStatus(c.getStatus().name());
    dto.setCreatedAt(c.getCreatedAt());
    return dto;
}
```

- [ ] **Step 6: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-app \
  -Dtest=SubmitCommentCmdExeTest -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 7: Commit**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/comment/
git add bytedepth-app/src/test/java/manfred/bytedepth/app/comment/
git commit -m "feat: Comment 应用层——新签名含 authorId/authorName 快照（TDD）"
```

---

## Task 9: 用户基础设施层（DO / Mapper / Repository）

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/UserDO.java`
- Create: `.../UserRoleDO.java`
- Create: `.../RoleDO.java`
- Create: `.../UserMapper.java`
- Create: `.../RoleMapper.java`
- Create: `.../UserRoleMapper.java`
- Create: `.../UserRepositoryImpl.java`

- [ ] **Step 1: 创建 DO 和 Mapper**

```java
// UserDO.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("`user`")   // user 是 MySQL 保留字，需要反引号
public class UserDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String email;
    private String avatar;
    private String bio;
    private String status;       // PENDING / ACTIVE / BANNED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
// RoleDO.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("role")
public class RoleDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}
```

```java
// UserRoleDO.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_role")
public class UserRoleDO {
    private Long userId;
    private Long roleId;
}
```

```java
// UserMapper.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    @Select("SELECT p.code FROM permission p " +
            "JOIN role_permission rp ON rp.permission_id = p.id " +
            "JOIN user_role ur ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
```

```java
// RoleMapper.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {}
```

```java
// UserRoleMapper.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {}
```

- [ ] **Step 2: 创建 UserRepositoryImpl**

```java
// UserRepositoryImpl.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(
            userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username))
        ).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUsername, username)) > 0;
    }

    @Override
    public List<User> findByStatus(UserStatus status) {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getStatus, status.name())
            .orderByAsc(UserDO::getCreatedAt))
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public User save(User user) {
        UserDO d = toDO(user);
        if (user.getId() == null) {
            userMapper.insert(d);
        } else {
            userMapper.updateById(d);
        }
        return toDomain(d);
    }

    @Override
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public void assignRole(Long userId, String roleName) {
        Long roleId = roleMapper.selectOne(
            new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getName, roleName)).getId();
        UserRoleDO ur = new UserRoleDO();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
    }

    private User toDomain(UserDO d) {
        return User.reconstruct(d.getId(), d.getUsername(), d.getPassword(),
            d.getEmail(), d.getAvatar(), d.getBio(),
            UserStatus.valueOf(d.getStatus()), d.getCreatedAt(), d.getUpdatedAt());
    }

    private UserDO toDO(User u) {
        UserDO d = new UserDO();
        d.setId(u.getId());
        d.setUsername(u.getUsername());
        d.setPassword(u.getPasswordHash());
        d.setEmail(u.getEmail());
        d.setAvatar(u.getAvatar());
        d.setBio(u.getBio());
        d.setStatus(u.getStatus().name());
        d.setCreatedAt(u.getCreatedAt() != null ? u.getCreatedAt() : LocalDateTime.now());
        d.setUpdatedAt(u.getUpdatedAt() != null ? u.getUpdatedAt() : LocalDateTime.now());
        return d;
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/
git commit -m "feat: 用户基础设施层——UserDO/RoleDO/Mapper/UserRepositoryImpl"
```

---

## Task 10: SiteUserDetails + SiteUserDetailsService（替换 AdminUserDetailsService）

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/SiteUserDetails.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/SiteUserDetailsService.java`
- Delete: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/admin/AdminUserDetailsService.java`

> 这是 RBAC 的核心接入点：登录时从 DB 动态加载用户权限（permission.code → GrantedAuthority）。

- [ ] **Step 1: 创建 SiteUserDetails（携带用户 ID）**

```java
// SiteUserDetails.java
package manfred.bytedepth.infrastructure.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class SiteUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public SiteUserDetails(Long id, String username, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    /** Controller 通过 @AuthenticationPrincipal SiteUserDetails 获取用户 ID */
    public Long getId() { return id; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

- [ ] **Step 2: 创建 SiteUserDetailsService**

```java
// SiteUserDetailsService.java
package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDO user = userMapper.selectOne(
            new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        if ("PENDING".equals(user.getStatus())) {
            throw new DisabledException("账号待管理员审核，请耐心等待");
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new LockedException("账号已被封禁");
        }
        List<SimpleGrantedAuthority> authorities =
            userMapper.selectPermissionCodesByUserId(user.getId())
                .stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new SiteUserDetails(user.getId(), user.getUsername(),
                                   user.getPassword(), authorities);
    }
}
```

- [ ] **Step 3: 删除 AdminUserDetailsService**

```bash
rm bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/admin/AdminUserDetailsService.java
```

- [ ] **Step 4: 编译确认（Spring Security 自动发现新的 UserDetailsService bean）**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 5: Commit**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/SiteUserDetails.java
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/SiteUserDetailsService.java
git rm bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/admin/AdminUserDetailsService.java
git commit -m "feat: SiteUserDetailsService——动态加载 RBAC 权限，替换 AdminUserDetailsService"
```

---

## Task 11: 修改 Post 基础设施层

**Files:**
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostDO.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java`

- [ ] **Step 1: 更新 PostDO（新增 authorId、featured）**

```java
@Data
@TableName("post")
public class PostDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;        // NEW
    private String title;
    private String content;
    private String status;
    private Boolean featured;     // NEW
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private Long categoryId;
    private Long seriesId;
    private Integer seriesOrder;
}
```

- [ ] **Step 2: 更新 PostRepositoryImpl**

2a. 更新 `toDO(Post)` 方法（追加新字段）：

```java
private PostDO toDO(Post post) {
    PostDO d = new PostDO();
    d.setId(post.getId());
    d.setAuthorId(post.getAuthorId());          // NEW
    d.setTitle(post.getTitle());
    d.setContent(post.getContent());
    d.setStatus(post.getStatus().name());
    d.setFeatured(post.getFeatured() != null ? post.getFeatured() : false); // NEW
    d.setCreatedAt(post.getCreatedAt());
    d.setPublishedAt(post.getPublishedAt());
    d.setUpdatedAt(post.getUpdatedAt());
    d.setCategoryId(post.getCategoryId());
    d.setSeriesId(post.getSeriesId());
    d.setSeriesOrder(post.getSeriesOrder());
    return d;
}
```

2b. 更新 `toEntity(PostDO)` 改调含 authorId+featured 的 `reconstruct` 重载：

```java
private Post toEntity(PostDO d) {
    return Post.reconstruct(
        d.getId(), d.getTitle(), d.getContent(),
        PostStatus.valueOf(d.getStatus()),
        d.getCreatedAt(), d.getPublishedAt(), d.getUpdatedAt(),
        d.getCategoryId(),
        d.getAuthorId(),   // NEW
        d.getFeatured()    // NEW
    );
}
```

2c. 追加两个新方法（实现 PostRepository 新接口）：

```java
@Override
public List<Post> findPublishedByAuthorId(Long authorId, int page, int size) {
    Page<PostDO> pageParam = new Page<>(page, size);
    return postMapper.selectPage(pageParam,
        new LambdaQueryWrapper<PostDO>()
            .eq(PostDO::getAuthorId, authorId)
            .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
            .orderByDesc(PostDO::getPublishedAt))
        .getRecords().stream().map(this::toEntity).collect(Collectors.toList());
}

@Override
public long countPublishedByAuthorId(Long authorId) {
    return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
        .eq(PostDO::getAuthorId, authorId)
        .eq(PostDO::getStatus, PostStatus.PUBLISHED.name()));
}
```

- [ ] **Step 3: 编译确认**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/
git commit -m "feat: Post 基础设施——authorId/featured 字段，findPublishedByAuthorId"
```

---

## Task 12: 修改 Comment 基础设施层

**Files:**
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/comment/CommentDO.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/comment/CommentRepositoryImpl.java`

- [ ] **Step 1: 更新 CommentDO**

```java
@Data
@TableName("comment")
public class CommentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long authorId;       // NEW（旧评论可为 null）
    private String authorName;   // 快照（保留）
    // authorEmail 已从表结构移除，对应字段删除
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 更新 CommentRepositoryImpl**

2a. 更新 `toDO(Comment)` 方法：

```java
private CommentDO toDO(Comment c) {
    CommentDO d = new CommentDO();
    d.setId(c.getId());
    d.setPostId(c.getPostId());
    d.setAuthorId(c.getAuthorId());     // NEW（可 null）
    d.setAuthorName(c.getAuthorName()); // 快照
    d.setContent(c.getContent());
    d.setStatus(c.getStatus().name());
    d.setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt() : LocalDateTime.now());
    return d;
}
```

2b. 更新 `toEntity(CommentDO)` 调用新的 `reconstruct` 签名：

```java
private Comment toEntity(CommentDO d) {
    return Comment.reconstruct(
        d.getId(), d.getPostId(),
        d.getAuthorId(),    // NEW（可 null）
        d.getAuthorName(),  // 快照
        d.getContent(),
        CommentStatus.valueOf(d.getStatus()),
        d.getCreatedAt()
    );
}
```

2c. 将 `CommentRepository` 接口中的 `findPending()` 替换为 `findAll(int page, int size)`：

```java
// CommentRepository.java 接口中：
// 移除：List<Comment> findPending();
// 新增：
List<Comment> findAll(int page, int size);
```

在 `CommentRepositoryImpl` 中实现：

```java
@Override
public List<Comment> findAll(int page, int size) {
    Page<CommentDO> pageParam = new Page<>(page, size);
    return commentMapper.selectPage(pageParam,
        new LambdaQueryWrapper<CommentDO>().orderByDesc(CommentDO::getCreatedAt))
        .getRecords().stream().map(this::toEntity).collect(Collectors.toList());
}
```

- [ ] **Step 3: 全量编译**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS（若有编译错误按提示修复调用方）
```

- [ ] **Step 4: Commit**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/comment/
git commit -m "feat: Comment 基础设施——authorId/authorName 快照，移除匿名字段"
```

---

## Task 13: 更新 SecurityConfig（启用方法级安全）

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`

- [ ] **Step 1: 替换 SecurityConfig 完整内容**

```java
package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 启用 @PreAuthorize
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/admin/search/**")
            )
            .authorizeHttpRequests(auth -> auth
                // /admin/** 需要后台访问权限（粗粒度守卫，具体操作靠 @PreAuthorize）
                .requestMatchers("/admin/**").hasAuthority("admin:dashboard:view")
                // 写操作需要认证（@PreAuthorize 再做细粒度）
                .requestMatchers(HttpMethod.POST, "/posts/*/comments").authenticated()
                .requestMatchers("/posts/new").authenticated()
                .requestMatchers(HttpMethod.POST, "/posts").authenticated()
                .requestMatchers(HttpMethod.POST, "/posts/*/publish").authenticated()
                // 其余全部放行
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)   // 登录成功回首页
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    res.sendRedirect("/login"))
                .accessDeniedHandler((req, res, ex) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN))
            );
        return http.build();
    }
}
```

- [ ] **Step 2: 为现有 AdminXxx 控制器所有方法追加 @PreAuthorize**

在以下文件的每个 `@GetMapping` / `@PostMapping` 上追加（批量搜索替换）：

```bash
# 检查哪些 admin 控制器需要加注解
grep -rn "@GetMapping\|@PostMapping\|@DeleteMapping" \
  bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/ \
  --include="*.java" | grep -v "AdminUserController"
```

对 `AdminDashboardController`、`AdminPostController`、`AdminCommentController`、`AdminCategoryController`、`AdminTagListController`、`AdminSeriesController`、`AdminSeriesDetailController`、`AdminSeriesListController`、`AdminProjectController`、`AdminSearchController`、`ImageController` 中的所有 Handler 方法，添加：

```java
@PreAuthorize("hasAuthority('admin:dashboard:view')")
```

（`AdminUserController` 的各方法在 Task 16 中单独设置更细的权限）

- [ ] **Step 3: 全量编译**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/
git commit -m "feat: SecurityConfig——启用 @EnableMethodSecurity，URL 规则简化"
```

---

## Task 14: RegisterController + 注册页（TDD）

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/RegisterController.java`
- Create: `bytedepth-start/src/main/resources/templates/public/register.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/RegisterControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// RegisterControllerTest.java
package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.user.RegisterUserCmdExe;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = RegisterController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class RegisterControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private RegisterUserCmdExe registerUserCmdExe;

    @Test
    void get_returnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/register"));
    }

    @Test
    void post_success_redirectsToLoginWithParam() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "alice")
                .param("password", "secret123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered=1"));

        verify(registerUserCmdExe).execute("alice", "secret123");
    }

    @Test
    void post_duplicateUsername_redirectsBackWithError() throws Exception {
        doThrow(new DomainException("用户名已存在：alice"))
            .when(registerUserCmdExe).execute("alice", "pass");

        mockMvc.perform(post("/register")
                .param("username", "alice")
                .param("password", "pass")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register?error=用户名已存在：alice"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=RegisterControllerTest -q 2>&1 | tail -5
# 期望：FAIL — RegisterController not found
```

- [ ] **Step 3: 创建 RegisterController**

```java
// RegisterController.java
package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.RegisterUserCmdExe;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterUserCmdExe registerUserCmdExe;

    @GetMapping("/register")
    public String showForm() {
        return "public/register";
    }

    @PostMapping("/register")
    public String submit(@RequestParam String username,
                         @RequestParam String password) {
        try {
            registerUserCmdExe.execute(username, password);
            return "redirect:/login?registered=1";
        } catch (DomainException e) {
            String encoded = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return "redirect:/register?error=" + encoded;
        }
    }
}
```

- [ ] **Step 4: 创建注册页模板**

`bytedepth-start/src/main/resources/templates/public/register.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title>注册 - bytedepth</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
               margin: 0; background: #1a1a2e;
               display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        .card { background: white; padding: 40px 48px; border-radius: 12px;
                width: 380px; box-shadow: 0 8px 32px rgba(0,0,0,.3); }
        h1 { margin: 0 0 4px; color: #1a1a2e; font-size: 1.6em; }
        .subtitle { color: #888; font-size: 0.9em; margin-bottom: 28px; }
        .field { margin-bottom: 16px; }
        label { display: block; font-size: 0.88em; font-weight: 500; color: #555; margin-bottom: 5px; }
        input { width: 100%; padding: 10px 14px; border: 1px solid #ddd;
                border-radius: 6px; font-size: 1em; outline: none; }
        input:focus { border-color: #e94560; }
        .btn { width: 100%; padding: 11px; background: #e94560; color: white;
               border: none; border-radius: 6px; font-size: 1em;
               font-weight: 600; cursor: pointer; margin-top: 8px; }
        .btn:hover { background: #c73652; }
        .error { background: #fff0f0; color: #c00; padding: 10px 14px;
                 border-radius: 6px; font-size: 0.9em; margin-bottom: 16px;
                 border-left: 3px solid #e94560; }
        .success { background: #ecfdf5; color: #065f46; padding: 10px 14px;
                   border-radius: 6px; font-size: 0.9em; margin-bottom: 16px;
                   border-left: 3px solid #2ecc71; }
        .hint { font-size: 0.8em; color: #aaa; margin-top: 8px; }
        .back { display: block; text-align: center; margin-top: 20px;
                color: #888; font-size: 0.9em; text-decoration: none; }
        .back:hover { color: #e94560; }
    </style>
</head>
<body>
<div class="card">
    <h1>bytedepth</h1>
    <p class="subtitle">创建账号</p>
    <div class="error" th:if="${param.error}" th:text="${param.error[0]}">错误信息</div>
    <form th:action="@{/register}" method="post">
        <div class="field">
            <label>用户名</label>
            <input type="text" name="username" autofocus
                   minlength="2" maxlength="50"
                   placeholder="2-50 个字符" required>
        </div>
        <div class="field">
            <label>密码</label>
            <input type="password" name="password"
                   minlength="6" placeholder="至少 6 位" required>
            <p class="hint">注册后需等待管理员审核，审核通过后方可登录。</p>
        </div>
        <button type="submit" class="btn">注册</button>
    </form>
    <a th:href="@{/login}" class="back">已有账号？去登录</a>
</div>
</body>
</html>
```

- [ ] **Step 5: 运行测试确认通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=RegisterControllerTest -q
# 期望：BUILD SUCCESS，3 tests passed
```

- [ ] **Step 6: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/RegisterController.java
git add bytedepth-start/src/main/resources/templates/public/register.html
git add bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/RegisterControllerTest.java
git commit -m "feat: 注册页——RegisterController + register.html（TDD）"
```

---

## Task 15: UserProfileController + 个人主页（TDD）

**Files:**
- Create: `bytedepth-adapter/.../portal/UserProfileController.java`
- Create: `bytedepth-start/.../templates/public/profile.html`
- Test: `bytedepth-start/.../adapter/web/portal/UserProfileControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// UserProfileControllerTest.java
package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.user.GetUserProfileQryExe;
import manfred.bytedepth.app.user.UserProfileDTO;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserProfileController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class UserProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private GetUserProfileQryExe getUserProfileQryExe;

    @Test
    void profile_existingUser_returnsProfileView() throws Exception {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(1L); dto.setUsername("alice");
        dto.setBio("Hello"); dto.setPostCount(3);
        dto.setRecentPosts(List.of());
        when(getUserProfileQryExe.execute("alice")).thenReturn(dto);

        mockMvc.perform(get("/u/alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/profile"))
            .andExpect(model().attribute("profile", dto));
    }

    @Test
    void profile_unknownUser_returns404() throws Exception {
        when(getUserProfileQryExe.execute("nobody"))
            .thenThrow(new DomainException("用户不存在：nobody"));

        mockMvc.perform(get("/u/nobody"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=UserProfileControllerTest -q 2>&1 | tail -5
# 期望：FAIL
```

- [ ] **Step 3: 创建 UserProfileController**

```java
// UserProfileController.java
package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.GetUserProfileQryExe;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/u")
@RequiredArgsConstructor
public class UserProfileController {

    private final GetUserProfileQryExe getUserProfileQryExe;

    @GetMapping("/{username}")
    public String profile(@PathVariable String username, Model model) {
        try {
            model.addAttribute("profile", getUserProfileQryExe.execute(username));
            return "public/profile";
        } catch (DomainException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 创建个人主页模板**

`bytedepth-start/src/main/resources/templates/public/profile.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title th:text="${profile.username} + ' - bytedepth'">个人主页</title>
    <style>
        :root { --bg:#f7f5f0; --card:#fff; --navy:#1a1a2e; --accent:#e94560;
                --border:#e7e4df; --ink:#1c1917; --ink-2:#57534e; --ink-3:#a8a29e;
                --sans:'Outfit',system-ui,sans-serif; --serif:'Source Serif 4',Georgia,serif; }
        * { box-sizing:border-box; margin:0; padding:0; }
        body { font-family:var(--serif); background:var(--bg); color:var(--ink); }
        .container { max-width:860px; margin:40px auto; padding:0 20px; }
        .profile-card { background:var(--card); border-radius:12px;
                        border:1px solid var(--border); padding:32px 40px;
                        box-shadow:0 2px 12px rgba(0,0,0,.06); margin-bottom:32px;
                        display:flex; align-items:center; gap:28px; }
        .avatar { width:72px; height:72px; border-radius:50%;
                  background:var(--navy); color:#fff; display:flex;
                  align-items:center; justify-content:center;
                  font-family:var(--sans); font-size:1.8rem; font-weight:700; flex-shrink:0; }
        .profile-info h1 { font-family:var(--sans); font-size:1.5rem; font-weight:700; }
        .profile-info .bio { color:var(--ink-2); margin-top:6px; font-size:0.95rem; }
        .profile-info .stats { margin-top:10px; font-family:var(--sans);
                               font-size:0.82rem; color:var(--ink-3); }
        .posts-section h2 { font-family:var(--sans); font-size:1rem; font-weight:700;
                            color:var(--ink); margin-bottom:16px; letter-spacing:.04em;
                            text-transform:uppercase; }
        .post-item { background:var(--card); padding:18px 24px; margin-bottom:10px;
                     border-radius:8px; border:1px solid var(--border);
                     transition:border-color .15s; }
        .post-item:hover { border-color:var(--navy); }
        .post-item a { font-family:var(--sans); font-weight:600; color:var(--ink);
                       text-decoration:none; font-size:1.05rem; }
        .post-item a:hover { color:var(--accent); }
        .post-meta { font-size:0.78rem; color:var(--ink-3); margin-top:4px;
                     font-family:var(--sans); }
        .empty { text-align:center; color:var(--ink-3); padding:40px;
                 font-family:var(--sans); }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <div class="profile-card">
        <div class="avatar" th:text="${#strings.substring(profile.username,0,1).toUpperCase()}">A</div>
        <div class="profile-info">
            <h1 th:text="${profile.username}">用户名</h1>
            <p class="bio" th:text="${profile.bio != null ? profile.bio : '这个人很懒，什么都没留下。'}">简介</p>
            <p class="stats" th:text="'共发布 ' + ${profile.postCount} + ' 篇文章'">文章数</p>
        </div>
    </div>
    <div class="posts-section">
        <h2>最近发布</h2>
        <div th:if="${#lists.isEmpty(profile.recentPosts)}" class="empty">
            还没有发布文章 ✍️
        </div>
        <div th:each="post : ${profile.recentPosts}" class="post-item">
            <a th:href="@{/posts/{id}(id=${post.id})}" th:text="${post.title}">文章标题</a>
            <div class="post-meta"
                 th:text="${#temporals.format(post.publishedAt, 'yyyy-MM-dd')}">日期</div>
        </div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 5: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=UserProfileControllerTest -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/UserProfileController.java
git add bytedepth-start/src/main/resources/templates/public/profile.html
git add bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/UserProfileControllerTest.java
git commit -m "feat: 个人主页——UserProfileController + profile.html（TDD）"
```

---

## Task 16: AdminUserController + 用户审核页（TDD）

**Files:**
- Create: `bytedepth-adapter/.../admin/AdminUserController.java`
- Create: `bytedepth-start/.../templates/admin/users/list.html`
- Test: `bytedepth-start/.../adapter/web/admin/AdminUserControllerTest.java`

- [ ] **Step 1: 写失败测试**

```java
// AdminUserControllerTest.java
package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.user.*;
import manfred.bytedepth.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminUserController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class AdminUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private ListPendingUsersQryExe listPendingUsersQryExe;
    @MockBean private ActivateUserCmdExe activateUserCmdExe;
    @MockBean private BanUserCmdExe banUserCmdExe;
    @MockBean private UserRepository userRepository;

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void list_returnsUsersView() throws Exception {
        when(listPendingUsersQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users/list"))
            .andExpect(model().attributeExists("pendingUsers"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void activate_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/users/1/activate").with(csrf()))
            .andExpect(redirectedUrl("/admin/users"));
        verify(activateUserCmdExe).execute(1L);
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void deletePending_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/users/1/delete").with(csrf()))
            .andExpect(redirectedUrl("/admin/users"));
        verify(userRepository).deleteById(1L);
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=AdminUserControllerTest -q 2>&1 | tail -5
```

- [ ] **Step 3: 创建 AdminUserController**

```java
// AdminUserController.java
package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final ListPendingUsersQryExe listPendingUsersQryExe;
    private final ActivateUserCmdExe activateUserCmdExe;
    private final BanUserCmdExe banUserCmdExe;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:approve')")
    public String list(Model model) {
        model.addAttribute("pendingUsers", listPendingUsersQryExe.execute());
        return "admin/users/list";
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('system:user:approve')")
    public String activate(@PathVariable Long id) {
        activateUserCmdExe.execute(id);
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('system:user:approve')")
    public String deletePending(@PathVariable Long id) {
        userRepository.deleteById(id);   // 拒绝注册：直接删除 PENDING 记录
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/ban")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public String ban(@PathVariable Long id) {
        banUserCmdExe.execute(id);
        return "redirect:/admin/users";
    }
}
```

- [ ] **Step 4: 创建用户审核列表模板**

`bytedepth-start/src/main/resources/templates/admin/users/list.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
    <title>用户审核 - bytedepth</title>
    <style>
        * { box-sizing:border-box; }
        body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
               margin:0; background:#f0f2f5; }
        .container { max-width:860px; margin:40px auto; padding:0 20px; }
        h1 { color:#1a1a2e; margin-bottom:24px; }
        .user-card { background:white; padding:16px 24px; margin:10px 0;
                     border-radius:8px; box-shadow:0 2px 8px rgba(0,0,0,.08);
                     display:flex; align-items:center; gap:16px; }
        .info { flex:1; }
        .username { font-weight:700; color:#1a1a2e; font-size:1rem; }
        .time { font-size:0.8rem; color:#aaa; margin-top:3px; }
        .actions { display:flex; gap:8px; }
        .btn-approve { padding:6px 16px; background:#2ecc71; color:white;
                       border:none; border-radius:4px; cursor:pointer; font-size:0.88em; }
        .btn-reject { padding:6px 16px; background:#e74c3c; color:white;
                      border:none; border-radius:4px; cursor:pointer; font-size:0.88em; }
        .empty { color:#888; text-align:center; padding:60px;
                 background:white; border-radius:8px; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <h1>待审核用户</h1>
    <div th:if="${#lists.isEmpty(pendingUsers)}" class="empty">
        暂无待审核用户 🎉
    </div>
    <div th:each="u : ${pendingUsers}" class="user-card">
        <div class="info">
            <div class="username" th:text="${u.username}">用户名</div>
            <div class="time"
                 th:text="'注册于 ' + ${#temporals.format(u.createdAt, 'yyyy-MM-dd HH:mm')}">时间</div>
        </div>
        <div class="actions">
            <form th:action="@{/admin/users/{id}/activate(id=${u.id})}" method="post">
                <button type="submit" class="btn-approve">通过</button>
            </form>
            <form th:action="@{/admin/users/{id}/delete(id=${u.id})}" method="post"
                  onsubmit="return confirm('确认拒绝并删除此注册申请？')">
                <button type="submit" class="btn-reject">拒绝</button>
            </form>
        </div>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 5: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=AdminUserControllerTest -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminUserController.java
git add bytedepth-start/src/main/resources/templates/admin/users/list.html
git add bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminUserControllerTest.java
git commit -m "feat: 用户审核后台——AdminUserController + admin/users/list.html（TDD）"
```

---

## Task 17: 修改 PostController（authorId 来自 SecurityContext）

**Files:**
- Modify: `bytedepth-adapter/.../portal/PostController.java`
- Modify: `bytedepth-start/src/test/java/.../portal/PostControllerTest.java`

- [ ] **Step 1: 更新 PostController 的 create 和 publish 方法**

```java
// 在 PostController.java 中更新：

// 1. 类头追加 import
import manfred.bytedepth.infrastructure.user.SiteUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// 2. newForm() 加认证守卫
@GetMapping("/new")
@PreAuthorize("hasAuthority('blog:post:create')")
public String newForm(Model model) {
    model.addAttribute("cmd", new CreatePostCmd());
    return "admin/posts/edit";
}

// 3. create() 获取 authorId 并设置到 cmd
@PostMapping
@PreAuthorize("hasAuthority('blog:post:create')")
public String create(@ModelAttribute CreatePostCmd cmd,
                     @AuthenticationPrincipal SiteUserDetails currentUser) {
    cmd.setAuthorId(currentUser.getId());
    Long id = createPostCmdExe.execute(cmd);
    return "redirect:/posts/" + id;
}

// 4. publish() 校验所有权（非 admin 只能发布自己的文章）
@PostMapping("/{id}/publish")
@PreAuthorize("isAuthenticated()")
public String publish(@PathVariable("id") Long id,
                      @AuthenticationPrincipal SiteUserDetails currentUser) {
    var post = getPostQryExe.execute(id);
    boolean isAdmin = currentUser.getAuthorities().stream()
        .anyMatch(a -> "blog:post:manage".equals(a.getAuthority()));
    if (!isAdmin && !currentUser.getId().equals(post.getAuthorId())) {
        throw new org.springframework.security.access.AccessDeniedException("无权发布他人文章");
    }
    publishPostCmdExe.execute(id);
    return "redirect:/posts/" + id;
}
```

- [ ] **Step 2: 更新 PostControllerTest 修复 MockBean 列表**

在 `PostControllerTest.java` 中确认 `@MockBean` 包含 `UserDetailsService` 和 `PasswordEncoder`（现有测试中应该已有），并为 `create` 和 `publish` 操作的测试使用 `@WithMockUser`：

```java
// 确保已有：
@MockBean private UserDetailsService userDetailsService;
@MockBean private PasswordEncoder passwordEncoder;

// 若 create/publish 测试不存在，追加：
@Test
@WithMockUser(authorities = "blog:post:create")
void create_authenticated_redirectsToPost() throws Exception {
    when(createPostCmdExe.execute(any())).thenReturn(5L);

    mockMvc.perform(post("/posts")
            .param("title", "Test")
            .param("content", "Content")
            .with(csrf()))
        .andExpect(redirectedUrl("/posts/5"));
}
```

- [ ] **Step 3: 更新 post-detail 模板中的发布按钮可见性**

在 `PostController.detail()` 方法中追加：

```java
// 在 detail() 中追加到 model（在 return 之前）：
boolean canPublish = false;
boolean canEdit   = false;
var authentication = org.springframework.security.core.context.SecurityContextHolder
    .getContext().getAuthentication();
if (authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof SiteUserDetails sd) {
    boolean isAdmin = sd.getAuthorities().stream()
        .anyMatch(a -> "blog:post:manage".equals(a.getAuthority()));
    boolean isOwner = sd.getId().equals(post.getAuthorId());
    canPublish = "DRAFT".equals(post.getStatus()) && (isOwner || isAdmin);
    canEdit    = isOwner || isAdmin;
}
model.addAttribute("canPublish", canPublish);
model.addAttribute("canEdit", canEdit);
```

- [ ] **Step 4: 全量编译**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 5: 运行所有应用测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest="PostControllerTest,HomeControllerTest" -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java
git add bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java
git commit -m "feat: PostController——authorId 来自 SecurityContext，publish 加所有权校验"
```

---

## Task 18: 修改 CommentController（认证用户，@PreAuthorize）

**Files:**
- Modify: `bytedepth-adapter/.../portal/CommentController.java`

- [ ] **Step 1: 替换 CommentController 完整内容**

```java
package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.infrastructure.user.SiteUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final SubmitCommentCmdExe submitCommentCmdExe;

    @PostMapping
    @PreAuthorize("hasAuthority('blog:comment:create')")
    public String submit(@PathVariable("postId") Long postId,
                         @RequestParam("content") String content,
                         @AuthenticationPrincipal SiteUserDetails currentUser) {
        submitCommentCmdExe.execute(
            postId,
            currentUser.getId(),
            currentUser.getUsername(),   // 快照用户名
            content
        );
        return "redirect:/posts/" + postId + "#comments";
    }
}
```

- [ ] **Step 2: 编译确认**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
```

- [ ] **Step 3: Commit**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/CommentController.java
git commit -m "feat: CommentController——认证用户发评论，@PreAuthorize，移除匿名参数"
```

---

## Task 19: 更新 Thymeleaf 模板

**Files:**
- Modify: `templates/fragments/nav.html`
- Modify: `templates/public/posts/detail.html`（评论区）
- Modify: `templates/public/login.html`
- Modify: `templates/admin/comments/list.html`

- [ ] **Step 1: 更新导航栏 nav.html**

将 `nav.html` 替换为以下内容（保留原有样式，仅更新链接逻辑）：

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<body>
<nav th:fragment="navbar"
     style="position:sticky;top:0;z-index:1000;padding:16px 24px;background:#1a1a2e;
            color:#eee;display:flex;gap:24px;align-items:center;">
    <a th:href="@{/}" style="display:flex;align-items:center;gap:8px;text-decoration:none;">
        <img th:src="@{/icons/logo.svg}" alt="bytedepth" width="36" height="36">
        <span style="color:#e94560;font-weight:bold;font-size:1.2em;letter-spacing:1px;">bytedepth</span>
    </a>
    <a th:href="@{/posts}" style="color:#eee;text-decoration:none;">文章</a>
    <a th:href="@{/columns}" style="color:#eee;text-decoration:none;">专栏</a>
    <a th:href="@{/projects}" style="color:#eee;text-decoration:none;">项目</a>
    <form action="/search" method="get" style="display:flex;margin-left:auto;">
        <input type="text" name="q" placeholder="搜索文章…"
               style="padding:5px 12px;border:none;border-radius:4px 0 0 4px;
                      font-size:0.88em;outline:none;width:160px;background:#fff;color:#333;" />
        <button type="submit"
                style="padding:5px 11px;background:#e94560;color:white;border:none;
                       border-radius:0 4px 4px 0;cursor:pointer;font-size:0.9em;">🔍</button>
    </form>
    <span style="display:flex;gap:16px;align-items:center;margin-left:16px;">
        <!-- 管理员专属 -->
        <a sec:authorize="hasAuthority('admin:dashboard:view')"
           th:href="@{/admin}"
           style="color:#e94560;text-decoration:none;font-weight:600;">后台</a>
        <a sec:authorize="hasAuthority('blog:post:create')"
           th:href="@{/posts/new}"
           style="color:#eee;text-decoration:none;">✏ 写文章</a>
        <a sec:authorize="hasAuthority('system:user:approve')"
           th:href="@{/admin/users}"
           style="color:#eee;text-decoration:none;font-size:0.88em;">👤 审核用户</a>
        <!-- 已登录用户 -->
        <a sec:authorize="isAuthenticated()"
           th:href="@{/u/{u}(u=${#authentication.name})}"
           style="color:#ccc;text-decoration:none;font-size:0.9em;"
           th:text="${#authentication.name}">用户名</a>
        <form sec:authorize="isAuthenticated()" th:action="@{/logout}" method="post" style="margin:0;">
            <button type="submit"
                    style="background:none;border:none;color:#aaa;cursor:pointer;font-size:0.9em;">退出</button>
        </form>
        <!-- 未登录 -->
        <a sec:authorize="!isAuthenticated()" th:href="@{/login}"
           style="color:#aaa;text-decoration:none;font-size:0.9em;">登录</a>
        <a sec:authorize="!isAuthenticated()" th:href="@{/register}"
           style="color:#e94560;text-decoration:none;font-size:0.9em;font-weight:600;">注册</a>
    </span>
</nav>
</body>
</html>
```

- [ ] **Step 2: 更新 post-detail.html 评论区**

在 `public/posts/detail.html` 中找到评论区 `<div class="comment-section">` 并替换评论表单部分：

```html
<!-- 评论列表（保持不变，c.authorName 依然有效） -->
<div th:each="c : ${comments}" class="comment-item">
    <a th:href="@{/u/{name}(name=${c.authorName})}"
       class="comment-author" th:text="${c.authorName}"
       style="text-decoration:none;color:inherit;">作者</a>
    <span class="comment-time"
          th:text="${#temporals.format(c.createdAt, 'yyyy-MM-dd HH:mm')}">时间</span>
    <div class="comment-body" th:text="${c.content}">内容</div>
</div>

<!-- 评论表单区域：已登录显示表单，未登录显示提示 -->
<div sec:authorize="hasAuthority('blog:comment:create')" class="comment-form">
    <!-- 移除评论已提交提示（不再有审核等待） -->
    <h4>发表评论</h4>
    <form th:action="@{/posts/{id}/comments(id=${post.id})}" method="post">
        <div class="form-row">
            <label>内容 *</label>
            <textarea name="content" required placeholder="写下你的想法..."></textarea>
        </div>
        <button type="submit" class="submit-btn">提交评论</button>
    </form>
</div>
<div sec:authorize="!isAuthenticated()"
     style="margin-top:20px;padding:16px 20px;background:#f7f5f0;
            border-radius:8px;border:1px solid #e7e4df;text-align:center;
            font-family:'Outfit',sans-serif;font-size:0.9rem;color:#57534e;">
    <a th:href="@{/login}" style="color:#e94560;font-weight:600;">登录</a> 或
    <a th:href="@{/register}" style="color:#e94560;font-weight:600;">注册</a>
    后才能发表评论
</div>
```

同时更新"发布此文章"按钮的可见性条件（使用 Task 17 中追加的 `canPublish` model 属性）：

```html
<!-- 替换原来的 th:if="${post.status == 'DRAFT'}" --> 
<div class="publish-form" th:if="${canPublish}">
    <form th:action="@{/posts/{id}/publish(id=${post.id})}" method="post">
        <button type="submit" class="btn">发布此文章</button>
    </form>
</div>
```

- [ ] **Step 3: 更新 login.html（改标题，加注册链接）**

```html
<!-- 修改两处 -->
<!-- 1. subtitle 改为通用 -->
<p class="subtitle">登录你的账号</p>

<!-- 2. 在 "← 返回博客" 链接前追加 -->
<div th:if="${param.registered}"
     style="background:#ecfdf5;color:#065f46;padding:10px 14px;border-radius:6px;
            font-size:0.9em;margin-bottom:16px;border-left:3px solid #2ecc71;">
    注册申请已提交，请等待管理员审核后再登录。
</div>
<div th:if="${param.error}"
     class="error">用户名或密码错误，或账号待审核 / 已封禁</div>
<a th:href="@{/register}" class="back" style="margin-top:12px;">还没有账号？去注册 →</a>
```

- [ ] **Step 4: 更新 admin/comments/list.html**

将"待审核评论"改为"评论列表"，移除审核按钮，改用全部评论：

```html
<!-- 修改 AdminCommentController 中的查询调用 -->
<!-- 在 AdminCommentController.list() 中：
     原：model.addAttribute("comments", listCommentsQryExe.findPending());
     改：model.addAttribute("comments", listCommentsQryExe.findAll(1, 50)); -->

<!-- 同时更新模板 admin/comments/list.html -->
<!-- 标题改为 -->
<h1>评论列表</h1>
<!-- 空状态改为 -->
<div th:if="${#lists.isEmpty(comments)}" class="empty">暂无评论</div>
<!-- 操作区：移除审核按钮（如需删除功能后续迭代添加） -->
<div class="comment-card" th:each="c : ${comments}">
    <div class="info">
        <span class="author" th:text="${c.authorName}">作者</span>
        <span class="post-link">来自：
            <a th:href="@{/posts/{id}(id=${c.postId})}"
               th:text="'文章 #' + ${c.postId}" target="_blank">文章</a>
        </span>
        <div class="time" th:text="${#temporals.format(c.createdAt,'yyyy-MM-dd HH:mm')}">时间</div>
        <div class="body" th:text="${c.content}">内容</div>
    </div>
    <!-- actions div 移除 -->
</div>
```

- [ ] **Step 5: 全量编译**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install \
  -DskipTests -Dsort.skip=true -q
# 期望：BUILD SUCCESS
```

- [ ] **Step 6: 运行全量单元测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -q
# 期望：BUILD SUCCESS，全部绿色
```

- [ ] **Step 7: Commit**

```bash
git add bytedepth-start/src/main/resources/templates/
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminCommentController.java
git commit -m "feat: 模板更新——导航栏登录/注册、评论区登录提示、admin 评论列表"
```

---

## Task 20: E2E 测试（Testcontainers 全链路）

**Files:**
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/AccountFlowE2ETest.java`

> 使用 `@SpringBootTest + @Testcontainers + MySQLContainer`（与现有 `PostRepositoryIT` 模式一致），Mock Redis 和 MeiliSearch，测试三条核心链路。

- [ ] **Step 1: 确认 Docker 已运行**

```bash
docker info > /dev/null 2>&1 && echo "Docker running" || echo "ERROR: Docker not running"
# 期望：Docker running（Testcontainers 需要 Docker）
```

- [ ] **Step 2: 创建 E2E 测试**

```java
// AccountFlowE2ETest.java
package manfred.bytedepth;

import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import manfred.bytedepth.infrastructure.stats.RedisStatsService;
import manfred.bytedepth.infrastructure.search.MeiliSearchPostIndexer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class AccountFlowE2ETest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @MockBean private RedisStatsService redisStatsService;
    @MockBean private MeiliSearchPostIndexer meiliSearchPostIndexer;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    // ─────────────────────────────────────────────
    // 链路 1：用户注册 → 账号处于 PENDING 状态
    // ─────────────────────────────────────────────
    @Test
    void registration_createsUserInPendingStatus() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "testuser_e2e")
                .param("password", "password123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered=1"));

        var user = userRepository.findByUsername("testuser_e2e");
        assertTrue(user.isPresent());
        assertEquals(UserStatus.PENDING, user.get().getStatus());
    }

    // ─────────────────────────────────────────────
    // 链路 2：PENDING 用户尝试登录 → 被拒绝（DisabledException）
    // ─────────────────────────────────────────────
    @Test
    void pendingUser_cannotLogin() throws Exception {
        // 先注册
        mockMvc.perform(post("/register")
                .param("username", "pending_login_test")
                .param("password", "pass123")
                .with(csrf()));

        // 尝试登录
        mockMvc.perform(post("/login")
                .param("username", "pending_login_test")
                .param("password", "pass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
    }

    // ─────────────────────────────────────────────
    // 链路 3：注册用户不能发表评论（需登录）
    // ─────────────────────────────────────────────
    @Test
    void anonymous_cannotPostComment() throws Exception {
        mockMvc.perform(post("/posts/1/comments")
                .param("content", "Hello!")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost/login"));
    }

    // ─────────────────────────────────────────────
    // 链路 4：管理员可以访问用户审核页
    // ─────────────────────────────────────────────
    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void adminUser_canAccessUserApprovalPage() throws Exception {
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users/list"));
    }

    // ─────────────────────────────────────────────
    // 链路 5：注册页正常加载
    // ─────────────────────────────────────────────
    @Test
    void registerPage_loadsSuccessfully() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/register"));
    }

    // ─────────────────────────────────────────────
    // 链路 6：重复注册同一用户名 → 重定向回注册页携带错误
    // ─────────────────────────────────────────────
    @Test
    void duplicateUsername_redirectsWithError() throws Exception {
        // 第一次注册
        mockMvc.perform(post("/register")
                .param("username", "dup_user")
                .param("password", "pass123")
                .with(csrf()));

        // 第二次注册同一用户名
        mockMvc.perform(post("/register")
                .param("username", "dup_user")
                .param("password", "other_pass")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/register?error=*"));
    }
}
```

- [ ] **Step 3: 运行 E2E 测试**

```bash
# 确保 Docker 运行中（Testcontainers 自动启动 MySQL）
docker info > /dev/null 2>&1 && echo "OK"

JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -pl bytedepth-start \
  -Dtest=AccountFlowE2ETest -q
# 期望：BUILD SUCCESS，6 tests passed
# 注意：首次运行会拉取 mysql:8.0 镜像，约需 30-60 秒
```

- [ ] **Step 4: 运行全量测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -q
# 期望：BUILD SUCCESS，全部绿色（含 domain、app、start 所有模块）
```

- [ ] **Step 5: Commit**

```bash
git add bytedepth-start/src/test/java/manfred/bytedepth/AccountFlowE2ETest.java
git commit -m "test: E2E——注册/PENDING/匿名评论拦截/管理员审核页（Testcontainers）"
```

---

## 最终验收

- [ ] **全量测试通过**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test \
  -Dsort.skip=true -q 2>&1 | tail -10
# 期望：BUILD SUCCESS，无 FAIL / ERROR
```

- [ ] **启动应用手工验证**

```bash
# 确保本地 MySQL（port 3306）和 Redis（6379）在运行
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package \
  -DskipTests -Dsort.skip=true -q
$(/usr/libexec/java_home -v 21)/bin/java \
  -jar bytedepth-start/target/bytedepth-start-*.jar

# 验证清单：
# 1. http://localhost:8080/register → 注册页正常
# 2. 注册一个用户 → 跳转到 /login?registered=1
# 3. 登录失败（账号待审核）→ /login?error
# 4. 访问 /login 以 admin 账号登录 → 进入后台
# 5. /admin/users → 看到待审核用户，点击"通过"
# 6. 切换为新用户登录 → 成功
# 7. 访问某篇文章，看到"登录后评论"变成评论表单
# 8. 提交评论 → 立即显示（无需审核）
# 9. 访问 /u/{username} → 个人主页正常展示
```

- [ ] **最终 Commit**

```bash
git add -A
git commit -m "chore: 账户系统与 RBAC 功能完成验收"
```

---

## 修复参考

### 常见编译问题

| 错误 | 原因 | 解决 |
|---|---|---|
| `Post.create()` 调用方报编译错 | 旧调用没传 authorId | 将 `Post.create(title, content)` 改为 `Post.create(title, content, authorId)` 或保留旧重载（authorId=null）|
| `Comment.create()` 参数不匹配 | 签名已更新 | 全局搜索替换调用方 |
| `AdminPostControllerTest` MockBean 缺失 | 需要 `SiteUserDetailsService` | `@MockBean UserDetailsService` 已能覆盖 |
| `user` 表名保留字报错 | `@TableName("user")` | 改为 `@TableName("`user`")` 带反引号 |
| `findPending()` 找不到 | 已重命名为 `findAll()` | 更新 `AdminCommentController` 调用 |
