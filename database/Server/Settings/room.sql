CREATE TABLE
  `room` (
    `guild_id` varchar(19) NOT NULL,
    `room_id` varchar(19) NOT NULL,
    `has_exp` tinyint(1) DEFAULT 1,
    `exp_value` double DEFAULT 1,
    `has_command_stats` tinyint(1) DEFAULT 1,
    PRIMARY KEY (`guild_id`, `room_id`),
    CONSTRAINT `room_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1