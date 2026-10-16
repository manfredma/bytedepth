ALTER TABLE post_annotation
    ADD COLUMN anchor_json LONGTEXT NULL COMMENT '不可变的版本化原始锚点；为空时仅创建者可见',
    ADD COLUMN migration_baseline_version BIGINT NULL COMMENT '历史迁移依据版本，不代表真实创建版本';

CREATE TABLE post_content_revision (
    post_id BIGINT NOT NULL,
    content_version BIGINT NOT NULL,
    markdown LONGTEXT NOT NULL,
    document_json LONGTEXT NOT NULL,
    baseline BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, content_version)
) COMMENT='不可变正文版本，基线不伪造过去历史';

CREATE TABLE annotation_request (
    post_id BIGINT NOT NULL,
    owner_key VARCHAR(80) NOT NULL,
    request_id CHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    annotation_id BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (post_id, owner_key, request_id),
    INDEX idx_annotation_request_annotation (annotation_id)
) COMMENT='创建幂等记录；批注删除后保留 tombstone';

CREATE TABLE annotation_placement (
    annotation_id BIGINT NOT NULL,
    target_revision BIGINT NOT NULL,
    projection_version INT NOT NULL,
    resolver_version INT NOT NULL,
    placement_json LONGTEXT NOT NULL,
    PRIMARY KEY (annotation_id, target_revision, projection_version, resolver_version)
) COMMENT='可重建的指定版本定位缓存，不是原始批注';
