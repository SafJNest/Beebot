CREATE TABLE
  `blacklist` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`),
    KEY `blacklist_guild_relation` (`guild_id`),
    CONSTRAINT `blacklist_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1