USE `auth`;

CREATE TABLE IF NOT EXISTS auth_transaction (
    state_hash CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    api_key_id CHAR(36) NOT NULL,
    redirect_uri VARCHAR(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (state_hash, api_key_id),
    INDEX auth_transaction_expiry (expires_at),
    INDEX auth_transaction_client (api_key_id),
    CONSTRAINT auth_transaction_key FOREIGN KEY (api_key_id) REFERENCES api_key(id)
) ENGINE=InnoDB;
