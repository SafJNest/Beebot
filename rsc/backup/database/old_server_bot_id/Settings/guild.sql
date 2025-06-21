CREATE TABLE
  `guild` (
    `guild_id` varchar(19) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    `prefix` varchar(32) NOT NULL DEFAULT '!',
    `name_tts` varchar(19) DEFAULT NULL,
    `language_tts` varchar(19) DEFAULT NULL,
    `exp_enabled` tinyint(1) NOT NULL DEFAULT 1,
    `threshold` tinyint(4) DEFAULT 0,
    `blacklist_channel` varchar(19) DEFAULT NULL,
    `blacklist_enabled` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`guild_id`, `bot_id`),
    KEY `bot` (`bot_id`),
    CONSTRAINT `bot` FOREIGN KEY (`bot_id`) REFERENCES `bots` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `guild` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1