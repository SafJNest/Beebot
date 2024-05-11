CREATE TABLE
  `guild` (
    `guild_id` varchar(19) NOT NULL,
    `prefix` varchar(32) NOT NULL DEFAULT '!',
    `name_tts` varchar(19) DEFAULT NULL,
    `language_tts` varchar(19) DEFAULT NULL,
    `exp_enabled` tinyint(1) NOT NULL DEFAULT 1,
    `threshold` tinyint(4) DEFAULT 0,
    `blacklist_channel` varchar(19) DEFAULT NULL,
    `blacklist_enabled` tinyint(1) NOT NULL DEFAULT 0,
    `league_shard` tinyint(4) NOT NULL DEFAULT 3,
    PRIMARY KEY (`guild_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1