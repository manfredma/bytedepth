CREATE TABLE IF NOT EXISTS `admin_user` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username`     VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    `password`     VARCHAR(200) NOT NULL COMMENT 'BCrypt 哈希',
    `role`         VARCHAR(50)  NOT NULL DEFAULT 'ROLE_ADMIN' COMMENT '角色',
    `created_at`   DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员账号';

-- 默认账号 admin / bytedepth2026
INSERT IGNORE INTO `admin_user` (username, password, role, created_at)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnBLMazCnkS', 'ROLE_ADMIN', NOW());
