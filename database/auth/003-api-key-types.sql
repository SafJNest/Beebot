USE `auth`;

ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS type ENUM('generic', 'personal') NOT NULL DEFAULT 'generic',
    ADD COLUMN IF NOT EXISTS discord_user_id VARCHAR(32) CHARACTER SET ascii NULL,
    ADD COLUMN IF NOT EXISTS user_json TEXT NULL;
