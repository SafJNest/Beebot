CREATE TABLE
  `alert` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `guild_id` varchar(19) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    `message` text NOT NULL DEFAULT '',
    `channel` varchar(19) DEFAULT NULL,
    `enabled` tinyint(4) NOT NULL,
    `type` tinyint(4) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `alert2_relation_1` (`guild_id`),
    KEY `alert2_relation_2` (`bot_id`),
    CONSTRAINT `alert2_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `alert2_relation_2` FOREIGN KEY (`bot_id`) REFERENCES `bots` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 22 DEFAULT CHARSET = latin1