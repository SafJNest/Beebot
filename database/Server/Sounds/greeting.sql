CREATE TABLE
  `greeting` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`, `bot_id`),
    KEY `greeting_relation_1` (`sound_id`),
    KEY `greeting_bot_relation` (`bot_id`),
    KEY `greeting_guild_relation` (`guild_id`),
    CONSTRAINT `greeting_bot_relation` FOREIGN KEY (`bot_id`) REFERENCES `bots` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `greeting_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1