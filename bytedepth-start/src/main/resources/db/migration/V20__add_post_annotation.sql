CREATE TABLE post_annotation (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    post_id         BIGINT       NOT NULL COMMENT '文章 ID',
    user_id         BIGINT       NOT NULL COMMENT '批注作者',
    selected_text   VARCHAR(500) NOT NULL COMMENT '被批注的文本',
    annotation_text TEXT         NOT NULL COMMENT '批注内容',
    color           VARCHAR(20)  NOT NULL DEFAULT 'yellow' COMMENT '高亮色：red/yellow/green/blue',
    start_offset    INT          NOT NULL COMMENT '正文文本起始偏移',
    end_offset      INT          NOT NULL COMMENT '正文文本结束偏移',
    created_at      DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_post_annotation_post (post_id),
    INDEX idx_post_annotation_user (user_id)
) COMMENT='文章批注';
