CREATE TABLE post_view_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL COMMENT '文章 ID',
    user_id     BIGINT                COMMENT '登录用户 ID，匿名为 NULL',
    ip          VARCHAR(64)           COMMENT '访客 IP',
    user_agent  VARCHAR(512)          COMMENT '浏览器标识',
    referer     VARCHAR(512)          COMMENT '来源页面',
    country     VARCHAR(64)           COMMENT 'IP 解析：国家',
    city        VARCHAR(64)           COMMENT 'IP 解析：城市',
    visited_at  DATETIME     NOT NULL COMMENT '访问时间',
    PRIMARY KEY (id),
    INDEX idx_post_id    (post_id),
    INDEX idx_user_id    (user_id),
    INDEX idx_visited_at (visited_at)
) COMMENT='文章访问日志';
