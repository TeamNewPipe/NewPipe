-- Schema v2 to v3: pending reviewer edits remain separate from published ratings.
-- Back up MySQL and apply after 001_developer_replies.sql, before deploying PHP.
CREATE TABLE IF NOT EXISTS wize_review_edits (
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    rating TINYINT UNSIGNED NOT NULL,
    nickname VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    app_version VARCHAR(128) NOT NULL,
    submitted_at BIGINT NOT NULL,
    CONSTRAINT fk_review_edit_author FOREIGN KEY (review_id)
        REFERENCES wize_reviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE wize_review_guard SET schema_version = 3 WHERE id = 1 AND schema_version = 2;
