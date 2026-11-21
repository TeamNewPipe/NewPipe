-- Import once with phpMyAdmin into the dedicated review database. MySQL 5.7+/MariaDB.
CREATE TABLE IF NOT EXISTS wize_review_guard (
    id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    schema_version TINYINT UNSIGNED NOT NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO wize_review_guard (id, schema_version) VALUES (1, 2);

CREATE TABLE IF NOT EXISTS wize_reviews (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    rating TINYINT UNSIGNED NOT NULL,
    nickname VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    app_version VARCHAR(128) NOT NULL,
    delete_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    state ENUM('pending', 'published') NOT NULL,
    created_day CHAR(10) CHARACTER SET ascii NOT NULL,
    submitted_at BIGINT NOT NULL,
    INDEX wize_review_state_date (state, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wize_review_replies (
    review_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    content TEXT NOT NULL,
    updated_day CHAR(10) CHARACTER SET ascii NOT NULL,
    CONSTRAINT fk_review_reply_author FOREIGN KEY (review_id)
        REFERENCES wize_reviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wize_review_challenges (
    digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    expires_at BIGINT NOT NULL,
    INDEX wize_challenge_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wize_review_limits (
    bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    attempts INT UNSIGNED NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX wize_limit_expiry (expires_at)
) ENGINE=InnoDB;
