CREATE DATABASE IF NOT EXISTS `auth` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `auth`;

CREATE TABLE api_key (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    key_hash CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    redirect_uris TEXT NOT NULL,
    type ENUM('generic', 'personal') NOT NULL DEFAULT 'generic',
    discord_user_id VARCHAR(32) CHARACTER SET ascii NULL,
    user_json TEXT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at TIMESTAMP(3) NULL DEFAULT NULL
) ENGINE=InnoDB;

CREATE TABLE auth_session (
    token_hash CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    api_key_id CHAR(36) NOT NULL,
    discord_user_id VARCHAR(32) CHARACTER SET ascii NOT NULL,
    encrypted_discord_token TEXT NOT NULL,
    user_json TEXT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at TIMESTAMP(3) NULL DEFAULT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX beebot_auth_session_client (api_key_id),
    INDEX beebot_auth_session_expiry (expires_at),
    CONSTRAINT beebot_auth_session_key FOREIGN KEY (api_key_id) REFERENCES api_key(id)
) ENGINE=InnoDB;

CREATE TABLE auth_transaction (
    state_hash CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    api_key_id CHAR(36) NOT NULL,
    redirect_uri VARCHAR(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (state_hash, api_key_id),
    INDEX auth_transaction_expiry (expires_at),
    INDEX auth_transaction_client (api_key_id),
    CONSTRAINT auth_transaction_key FOREIGN KEY (api_key_id) REFERENCES api_key(id)
) ENGINE=InnoDB;
