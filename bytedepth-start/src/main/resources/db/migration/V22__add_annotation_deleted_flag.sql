ALTER TABLE post_annotation
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除' AFTER created_at;