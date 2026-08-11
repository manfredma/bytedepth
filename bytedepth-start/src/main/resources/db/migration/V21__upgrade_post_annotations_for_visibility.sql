ALTER TABLE post_annotation
    MODIFY COLUMN user_id BIGINT NULL COMMENT '登录用户批注作者，匿名时为空',
    MODIFY COLUMN annotation_text TEXT NULL COMMENT '批注评论；纯划线时为空',
    ADD COLUMN owner_token_hash CHAR(64) NULL COMMENT '匿名浏览器身份令牌的 SHA-256 摘要' AFTER user_id,
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PRIVATE 或 PUBLIC' AFTER color,
    ADD INDEX idx_post_annotation_visible (post_id, visibility),
    ADD INDEX idx_post_annotation_owner_token (owner_token_hash);
