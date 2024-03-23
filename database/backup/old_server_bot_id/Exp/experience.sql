CREATE TABLE
  `experience` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    `exp` mediumint(9) NOT NULL,
    `level` smallint(6) NOT NULL,
    `messages` mediumint(9) DEFAULT NULL,
    PRIMARY KEY (`user_id`, `guild_id`),
    KEY `experience_guild_relation` (`guild_id`),
    CONSTRAINT `experience_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1