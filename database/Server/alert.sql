CREATE TABLE
  `alert` (
    `guild_id` varchar(19) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    `boost_channel` varchar(19) DEFAULT NULL,
    `leave_channel` varchar(19) DEFAULT NULL,
    `welcome_channel` varchar(19) DEFAULT NULL,
    `boost_message` text DEFAULT NULL,
    `leave_message` text DEFAULT NULL,
    `levelup_message` text DEFAULT NULL,
    `welcome_message` text DEFAULT NULL,
    `boost_enabled` tinyint(1) DEFAULT 0,
    `leave_enabled` tinyint(1) DEFAULT 0,
    `levelup_enabled` tinyint(1) DEFAULT 0,
    `welcome_enabled` tinyint(1) DEFAULT 0,
    `welcome_role` varchar(19) DEFAULT NULL,
    PRIMARY KEY (`guild_id`, `bot_id`),
    KEY `alert_bot_relation` (`bot_id`),
    CONSTRAINT `alert_bot_relation` FOREIGN KEY (`bot_id`) REFERENCES `bots` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `alert_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1