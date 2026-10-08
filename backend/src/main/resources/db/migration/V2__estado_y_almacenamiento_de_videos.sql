ALTER TABLE videos
    ADD COLUMN status       VARCHAR(20)  NOT NULL DEFAULT 'UPLOADING' AFTER visibility,
    ADD COLUMN storage_key  VARCHAR(255) NULL AFTER status,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER storage_key,
    ADD COLUMN size_bytes   BIGINT       NULL AFTER content_type;

ALTER TABLE videos ALTER COLUMN status DROP DEFAULT;