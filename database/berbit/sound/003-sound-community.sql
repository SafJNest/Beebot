-- Apply after 001 and 002, with the bot and API stopped. See docs/api/beebot-sounds.md.
-- For installations with the renamed columns, start from 004 and 005.
-- Use the real sound ID type: older installations and exported schemas differ.
SELECT COLUMN_TYPE INTO @sound_id_type
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sound' AND COLUMN_NAME = 'id';

ALTER TABLE sound
    ADD COLUMN IF NOT EXISTS audio_key VARCHAR(255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS image_key VARCHAR(255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    ADD INDEX IF NOT EXISTS sound_public_plays (public, plays, id),
    ADD INDEX IF NOT EXISTS sound_public_time (public, time, id),
    ADD INDEX IF NOT EXISTS sound_author_public (user_id, public, id);

SET @ddl = CONCAT('CREATE TABLE IF NOT EXISTS sound_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sound_id ', @sound_id_type, ' NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    author_id VARCHAR(19) NOT NULL,
    body VARCHAR(2000) DEFAULT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    edited_at TIMESTAMP(6) NULL DEFAULT NULL,
    deleted_at TIMESTAMP(6) NULL DEFAULT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    hearted BOOLEAN NOT NULL DEFAULT FALSE,
    likes INT UNSIGNED NOT NULL DEFAULT 0,
    dislikes INT UNSIGNED NOT NULL DEFAULT 0,
    reply_count INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY sound_comment_date (sound_id, parent_id, created_at, id),
    KEY sound_comment_likes (sound_id, parent_id, likes, created_at, id),
    KEY sound_comment_dislikes (sound_id, parent_id, dislikes, created_at, id),
    KEY sound_comment_pin (sound_id, pinned),
    CONSTRAINT sound_comment_sound_fk FOREIGN KEY (sound_id)
        REFERENCES sound(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT sound_comment_root_pin CHECK (pinned = 0 OR parent_id IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin');
PREPARE sound_ddl FROM @ddl;
EXECUTE sound_ddl;
DEALLOCATE PREPARE sound_ddl;

SET @ddl = CONCAT('CREATE TABLE IF NOT EXISTS sound_vote (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sound_id ', @sound_id_type, ' DEFAULT NULL,
    comment_id BIGINT DEFAULT NULL,
    user_id VARCHAR(19) NOT NULL,
    value TINYINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY sound_vote_user (sound_id, user_id),
    UNIQUE KEY comment_vote_user (comment_id, user_id),
    CONSTRAINT sound_vote_sound_fk FOREIGN KEY (sound_id)
        REFERENCES sound(id) ON DELETE CASCADE,
    CONSTRAINT sound_vote_comment_fk FOREIGN KEY (comment_id)
        REFERENCES sound_comment(id) ON DELETE CASCADE,
    CONSTRAINT sound_vote_target CHECK ((sound_id IS NULL) <> (comment_id IS NULL)),
    CONSTRAINT sound_vote_value CHECK (value IN (-1, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin');
PREPARE sound_ddl FROM @ddl;
EXECUTE sound_ddl;
DEALLOCATE PREPARE sound_ddl;

-- Preserve event IDs, timestamps, attribution and existing indexes.
RENAME TABLE IF EXISTS sound_history TO sound_play;
ALTER TABLE sound_play
    MODIFY COLUMN user_id VARCHAR(19) NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS channel_id VARCHAR(19) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS playback_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD UNIQUE INDEX IF NOT EXISTS sound_play_retry (sound_id, playback_id),
    ADD INDEX IF NOT EXISTS sound_play_recent (time, sound_id);

-- Keep the source table as a cutover backup. After it has been renamed, a retry
-- skips the transfer so old votes cannot overwrite newer writes.
SET @legacy_votes = EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sound_interactions'
);

START TRANSACTION;
SET @copy_votes = IF(@legacy_votes,
    'INSERT INTO sound_vote (sound_id, user_id, value)
     SELECT sound_id, user_id, value FROM sound_interactions WHERE value IN (-1, 1)
     ON DUPLICATE KEY UPDATE value = VALUES(value)',
    'SELECT 1');
PREPARE sound_copy FROM @copy_votes;
EXECUTE sound_copy;
DEALLOCATE PREPARE sound_copy;

UPDATE sound s
LEFT JOIN (
    SELECT sound_id, SUM(value = 1) AS likes, SUM(value = -1) AS dislikes
    FROM sound_vote WHERE sound_id IS NOT NULL GROUP BY sound_id
) votes ON votes.sound_id = s.id
SET s.likes = COALESCE(votes.likes, 0), s.dislikes = COALESCE(votes.dislikes, 0)
WHERE @legacy_votes;
COMMIT;

RENAME TABLE IF EXISTS sound_interactions TO sound_interactions_before_003;
