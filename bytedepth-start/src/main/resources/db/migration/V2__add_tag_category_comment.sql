CREATE TABLE IF NOT EXISTS `category` (
    `id`        BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`      VARCHAR(100) NOT NULL COMMENT '分类名称',
    `slug`      VARCHAR(100) NOT NULL UNIQUE COMMENT 'URL 标识',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类 ID（NULL 为顶级）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章分类';

CREATE TABLE IF NOT EXISTS `tag` (
    `id`   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL UNIQUE COMMENT '标签名',
    `slug` VARCHAR(100) NOT NULL UNIQUE COMMENT 'URL 标识'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签';

CREATE TABLE IF NOT EXISTS `post_tag` (
    `post_id` BIGINT NOT NULL,
    `tag_id`  BIGINT NOT NULL,
    PRIMARY KEY (`post_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-标签关联';

ALTER TABLE `post`
    ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '所属分类';

CREATE TABLE IF NOT EXISTS `comment` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `post_id`      BIGINT NOT NULL COMMENT '所属文章',
    `author_name`  VARCHAR(100) NOT NULL COMMENT '评论者昵称',
    `author_email` VARCHAR(200) COMMENT '评论者邮箱',
    `content`      TEXT NOT NULL COMMENT '评论内容',
    `status`       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `created_at`   DATETIME NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';
