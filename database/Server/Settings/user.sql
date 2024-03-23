CREATE TABLE
  `user` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    `experience` bigint(20) NOT NULL DEFAULT 0,
    `level` smallint(6) NOT NULL DEFAULT 0,
    `messages` mediumint(9) NOT NULL DEFAULT 0,
    `update_time` int(11) NOT NULL DEFAULT 60,
    PRIMARY KEY (`id`),
    KEY `user_relation_1` (`guild_id`),
    CONSTRAINT `user_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 343 DEFAULT CHARSET = latin1