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

-- 16. 设置 author_id NOT NULL（数据已填充完毕）
ALTER TABLE `post` MODIFY COLUMN `author_id` BIGINT NOT NULL;
