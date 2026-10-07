CREATE TABLE post_reading_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    session_id CHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    active_seconds_delta INT NOT NULL DEFAULT 0,
    max_scroll_depth TINYINT NOT NULL DEFAULT 0,
    occurred_at DATETIME NULL,
    received_at DATETIME NOT NULL,
    projected_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_reading_event_event_id (event_id),
    KEY ix_post_reading_event_pending (projected_at, received_at, id),
    KEY ix_post_reading_event_retention (received_at),
    KEY ix_post_reading_event_user (user_id),
    CONSTRAINT fk_post_reading_event_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_reading_event_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
);

CREATE TABLE user_post_reading_history (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    read_count BIGINT NOT NULL DEFAULT 0,
    total_active_seconds BIGINT NOT NULL DEFAULT 0,
    first_read_at DATETIME NOT NULL,
    last_read_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (user_id, post_id),
    KEY ix_user_post_reading_history_order (user_id, last_read_at, post_id),
    CONSTRAINT fk_user_post_reading_history_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_post_reading_history_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
);
