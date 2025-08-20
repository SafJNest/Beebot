CREATE TABLE
  `greeting` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`),
    KEY `greeting_relation_1` (`guild_id`),
    KEY `greeting_relation_2` (`sound_id`),
    CONSTRAINT `greeting_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `greeting_relation_2` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1