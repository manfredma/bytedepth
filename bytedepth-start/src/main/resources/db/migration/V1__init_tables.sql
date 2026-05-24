CREATE TABLE IF NOT EXISTS `post` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title`        VARCHAR(255) NOT NULL COMMENT '标题',
    `content`      LONGTEXT COMMENT 'Markdown 正文',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/DELETED',
    `created_at`   DATETIME     NOT NULL COMMENT '创建时间',
    `published_at` DATETIME              COMMENT '发布时间',
    `updated_at`   DATETIME     NOT NULL COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博文表';
