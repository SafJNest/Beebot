CREATE TABLE
  `alert` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `guild_id` varchar(19) NOT NULL,
    `message` text NOT NULL DEFAULT '',
    `channel` varchar(19) DEFAULT NULL,
    `enabled` tinyint(4) NOT NULL,
    `type` tinyint(4) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `alert2_relation_1` (`guild_id`),
    CONSTRAINT `alert_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 63 DEFAULT CHARSET = latin1