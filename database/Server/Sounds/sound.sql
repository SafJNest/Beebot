CREATE TABLE
  `sound` (
    `id` smallint(6) NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `extension` varchar(4) NOT NULL,
    `public` tinyint(1) DEFAULT 0,
    `time` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `sound_guild_relation` (`guild_id`),
    CONSTRAINT `sound_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 209 DEFAULT CHARSET = latin1