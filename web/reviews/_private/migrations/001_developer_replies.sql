-- Existing schema v1 to v2. Back up MySQL, then run before uploading PHP.
CREATE TABLE IF NOT EXISTS wize_review_replies (
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    content TEXT NOT NULL,
    updated_day CHAR(10) CHARACTER SET ascii NOT NULL,
    CONSTRAINT fk_review_reply_author FOREIGN KEY (review_id)
        REFERENCES wize_reviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE wize_review_guard SET schema_version = 2 WHERE id = 1 AND schema_version = 1;
