CREATE TABLE post_rating (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,
    visitor_token VARCHAR(64)  NOT NULL,
    score         TINYINT      NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_rating_visitor (post_id, visitor_token),
    KEY idx_post_rating_post (post_id),
    CONSTRAINT chk_post_rating_score CHECK (score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章匿名评分';
