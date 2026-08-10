CREATE TABLE page_view_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    page_path   VARCHAR(255) NOT NULL COMMENT '页面路径，如 /about',
    user_id     BIGINT                COMMENT '登录用户 ID，匿名为 NULL',
    ip          VARCHAR(64)           COMMENT '访客 IP',
    user_agent  VARCHAR(512)          COMMENT '浏览器标识',
    referer     VARCHAR(512)          COMMENT '来源页面',
    country     VARCHAR(64)           COMMENT 'IP 解析：国家',
    city        VARCHAR(64)           COMMENT 'IP 解析：城市',
    visited_at  DATETIME     NOT NULL COMMENT '访问时间',
    PRIMARY KEY (id),
    INDEX idx_page_view_log_path    (page_path),
    INDEX idx_page_view_log_user    (user_id),
    INDEX idx_page_view_log_visited (visited_at)
) COMMENT='页面访问日志';
