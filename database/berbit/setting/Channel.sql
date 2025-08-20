CREATE TABLE
  `channel` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `guild_id` varchar(19) NOT NULL,
    `channel_id` varchar(19) NOT NULL,
    `exp_modifier` double NOT NULL DEFAULT 1,
    `exp_enabled` tinyint(4) NOT NULL DEFAULT 1,
    `stats_enabled` tinyint(4) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `channel_relation_1` (`guild_id`),
    CONSTRAINT `channel_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 49 DEFAULT CHARSET = latin1