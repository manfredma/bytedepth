ALTER TABLE post
    ADD COLUMN content_version INT NOT NULL DEFAULT 1;

UPDATE post
SET content_version = CASE
    WHEN updated_at <> created_at THEN 2
    ELSE 1
END;
