CREATE TABLE
  `greeting` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`, `bot_id`),
    KEY `greeting_relation_1` (`sound_id`),
    CONSTRAINT `greeting_relation_1` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1