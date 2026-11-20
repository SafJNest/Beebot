-- Apply once after 005 with the bot and API stopped.
-- Types match the 005 MariaDB dump: soundboard.id SMALLINT signed, sound.id INT UNSIGNED.
ALTER TABLE soundboard DROP FOREIGN KEY soundboard_relation_1;

ALTER TABLE soundboard
    MODIFY COLUMN guild_id VARCHAR(19) DEFAULT NULL,
    ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sort_mode VARCHAR(12) NOT NULL DEFAULT 'NAME_ASC',
    ADD COLUMN likes BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN plays BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN comments BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN personal_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
        AS (IF(guild_id IS NULL, name, NULL)) PERSISTENT,
    ADD UNIQUE KEY soundboard_personal_name (user_id, personal_name),
    ADD KEY soundboard_public_id (public, id),
    ADD KEY soundboard_guild_name (guild_id, name),
    ADD CONSTRAINT soundboard_relation_1 FOREIGN KEY (guild_id)
        REFERENCES guild(guild_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE soundboard_sounds ADD COLUMN position SMALLINT UNSIGNED DEFAULT NULL;

UPDATE soundboard_sounds AS target
JOIN (
    SELECT ss.id, ss.sound_id, ROW_NUMBER() OVER (PARTITION BY ss.id ORDER BY s.name ASC, s.id ASC) AS row_position
    FROM soundboard_sounds AS ss JOIN sound AS s ON s.id = ss.sound_id
) AS ranked ON ranked.id = target.id AND ranked.sound_id = target.sound_id
SET target.position = ranked.row_position;

ALTER TABLE soundboard_sounds
    MODIFY COLUMN position SMALLINT UNSIGNED NOT NULL,
    ADD UNIQUE KEY soundboard_position (id, position);

ALTER TABLE sound_comment DROP FOREIGN KEY sound_comment_sound_fk;

ALTER TABLE sound_comment
    MODIFY COLUMN sound_id INT UNSIGNED DEFAULT NULL,
    ADD COLUMN soundboard_id SMALLINT DEFAULT NULL,
    ADD KEY soundboard_comment_date (soundboard_id, parent_id, created_at, id),
    ADD KEY soundboard_comment_likes (soundboard_id, parent_id, likes, created_at, id),
    ADD KEY soundboard_comment_dislikes (soundboard_id, parent_id, dislikes, created_at, id),
    ADD KEY soundboard_comment_pin (soundboard_id, pinned),
    ADD CONSTRAINT sound_comment_sound_fk FOREIGN KEY (sound_id)
        REFERENCES sound(id) ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT sound_comment_soundboard_fk FOREIGN KEY (soundboard_id)
        REFERENCES soundboard(id) ON DELETE CASCADE,
    ADD CONSTRAINT sound_comment_resource CHECK ((sound_id IS NOT NULL) + (soundboard_id IS NOT NULL) = 1);

ALTER TABLE sound_vote
    DROP CONSTRAINT IF EXISTS sound_vote_target,
    ADD COLUMN soundboard_id SMALLINT DEFAULT NULL,
    ADD UNIQUE KEY soundboard_vote_user (soundboard_id, user_id),
    ADD CONSTRAINT sound_vote_soundboard_fk FOREIGN KEY (soundboard_id)
        REFERENCES soundboard(id) ON DELETE CASCADE,
    ADD CONSTRAINT sound_vote_resource CHECK ((sound_id IS NOT NULL) + (soundboard_id IS NOT NULL) + (comment_id IS NOT NULL) = 1),
    ADD CONSTRAINT sound_vote_soundboard_like CHECK (soundboard_id IS NULL OR value = 1);

ALTER TABLE sound_play
    ADD COLUMN soundboard_id SMALLINT DEFAULT NULL,
    ADD KEY soundboard_play_time (soundboard_id, time, id),
    ADD CONSTRAINT sound_play_soundboard_fk FOREIGN KEY (soundboard_id)
        REFERENCES soundboard(id) ON DELETE SET NULL;
