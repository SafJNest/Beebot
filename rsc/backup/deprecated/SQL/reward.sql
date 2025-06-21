CREATE TABLE
  `reward` (
    `guild_id` varchar(19) NOT NULL,
    `role_id` varchar(19) NOT NULL,
    `level` smallint(6) NOT NULL,
    `message_text` varchar(255) DEFAULT NULL,
    PRIMARY KEY (`guild_id`, `role_id`),
    CONSTRAINT `reward_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1