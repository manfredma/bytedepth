CREATE TABLE IF NOT EXISTS `project` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '项目名称',
    `description` TEXT COMMENT '项目描述',
    `tech_stack`  VARCHAR(500) COMMENT '技术栈标签，逗号分隔',
    `github_url`  VARCHAR(500) COMMENT 'GitHub 地址',
    `demo_url`    VARCHAR(500) COMMENT '演示地址',
    `sort_order`  INT NOT NULL DEFAULT 0 COMMENT '排序，数字越小越靠前',
    `created_at`  DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目展示';

CREATE TABLE IF NOT EXISTS `page_stats` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `path`       VARCHAR(200) NOT NULL UNIQUE COMMENT '页面路径',
    `pv_count`   BIGINT NOT NULL DEFAULT 0 COMMENT '访问次数',
    `updated_at` DATETIME NOT NULL COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面访问统计';
