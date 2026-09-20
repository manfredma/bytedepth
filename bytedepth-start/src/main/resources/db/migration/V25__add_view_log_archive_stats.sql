CREATE TABLE post_view_hourly_stat (
    stat_hour  DATETIME NOT NULL COMMENT '小时起点（Asia/Shanghai）',
    post_id    BIGINT   NOT NULL COMMENT '文章 ID',
    view_count BIGINT   NOT NULL DEFAULT 0 COMMENT '该小时文章 PV',
    PRIMARY KEY (stat_hour, post_id),
    INDEX idx_post_view_hourly_post_hour (post_id, stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章小时访问统计';

CREATE TABLE page_view_hourly_stat (
    stat_hour  DATETIME     NOT NULL COMMENT '小时起点（Asia/Shanghai）',
    page_path  VARCHAR(255) NOT NULL COMMENT '页面路径',
    view_count BIGINT       NOT NULL DEFAULT 0 COMMENT '该小时页面 PV',
    PRIMARY KEY (stat_hour, page_path),
    INDEX idx_page_view_hourly_path_hour (page_path, stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面小时访问统计';

CREATE TABLE post_view_country_daily_stat (
    stat_date  DATE         NOT NULL COMMENT '自然日（Asia/Shanghai）',
    post_id    BIGINT       NOT NULL COMMENT '文章 ID',
    country    VARCHAR(64)  NOT NULL COMMENT '国家，空值统一为 未知',
    view_count BIGINT       NOT NULL DEFAULT 0 COMMENT '该日该文章该国家 PV',
    PRIMARY KEY (stat_date, post_id, country),
    INDEX idx_post_view_country_post_date (post_id, stat_date),
    INDEX idx_post_view_country_country_date (country, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章国家日访问统计';

CREATE TABLE page_view_country_daily_stat (
    stat_date  DATE         NOT NULL COMMENT '自然日（Asia/Shanghai）',
    page_path  VARCHAR(255) NOT NULL COMMENT '页面路径',
    country    VARCHAR(64)  NOT NULL COMMENT '国家，空值统一为 未知',
    view_count BIGINT       NOT NULL DEFAULT 0 COMMENT '该日该页面该国家 PV',
    PRIMARY KEY (stat_date, page_path, country),
    INDEX idx_page_view_country_path_date (page_path, stat_date),
    INDEX idx_page_view_country_country_date (country, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面国家日访问统计';

CREATE TABLE view_log_archive_bucket (
    source             VARCHAR(16) NOT NULL COMMENT 'post 或 page',
    bucket_start       DATETIME    NOT NULL COMMENT '已处理小时起点',
    archived_at        DATETIME    NOT NULL COMMENT '最近一次归档完成时间',
    archived_row_count BIGINT      NOT NULL DEFAULT 0 COMMENT '本次删除明细数',
    PRIMARY KEY (source, bucket_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志小时归档状态';

CREATE TABLE view_log_tablespace_state (
    source                     VARCHAR(16) NOT NULL COMMENT 'post 或 page',
    deleted_rows_since_optimize BIGINT      NOT NULL DEFAULT 0 COMMENT '上次成功维护后的累计删除数',
    last_optimized_at          DATETIME                COMMENT '最近一次成功维护时间',
    updated_at                 DATETIME    NOT NULL COMMENT '状态更新时间',
    PRIMARY KEY (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志表空间维护状态';
