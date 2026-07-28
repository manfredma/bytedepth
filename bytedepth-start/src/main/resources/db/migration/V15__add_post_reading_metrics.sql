ALTER TABLE post_view_log
    ADD COLUMN visit_token VARCHAR(64) NULL COMMENT '单次访问随机标识' AFTER visited_at,
    ADD COLUMN active_read_seconds INT NOT NULL DEFAULT 0 COMMENT '累计有效阅读秒数' AFTER visit_token,
    ADD COLUMN max_scroll_depth TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最大阅读深度，0-100' AFTER active_read_seconds,
    ADD COLUMN last_activity_at DATETIME NULL COMMENT '最后有效阅读活动时间' AFTER max_scroll_depth,
    ADD COLUMN completed_at DATETIME NULL COMMENT '首次完成阅读时间' AFTER last_activity_at,
    ADD UNIQUE INDEX uk_post_view_log_visit_token (visit_token),
    ADD INDEX idx_post_view_log_reading (post_id, active_read_seconds);
