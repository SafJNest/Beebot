-- If soundboard still has its old BLOB and thumbnail_key, archive the BLOB as thumbnail_legacy first.
ALTER TABLE sound
    RENAME COLUMN IF EXISTS audio_key TO url,
    RENAME COLUMN IF EXISTS image_key TO thumbnail,
    RENAME COLUMN IF EXISTS comment_count TO comments,
    MODIFY COLUMN description VARCHAR(500) CHARACTER SET utf8mb4 DEFAULT NULL;

ALTER TABLE sound_comment
    RENAME COLUMN IF EXISTS author_id TO user_id,
    RENAME COLUMN IF EXISTS reply_count TO replies;

ALTER TABLE soundboard
    RENAME COLUMN IF EXISTS thumbnail_key TO thumbnail;
