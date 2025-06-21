CREATE TABLE
  `blacklist` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`),
    KEY `blacklist_guild_relation` (`guild_id`),
    CONSTRAINT `blacklist_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1