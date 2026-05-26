CREATE TABLE IF NOT EXISTS `series` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '系列名称',
    `slug`        VARCHAR(100) NOT NULL UNIQUE COMMENT 'URL 标识',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '系列简介'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章系列';

ALTER TABLE `post`
    ADD COLUMN `series_id`    BIGINT DEFAULT NULL COMMENT '所属系列',
    ADD COLUMN `series_order` INT    DEFAULT NULL COMMENT '系列内排序（从 1 开始）';
