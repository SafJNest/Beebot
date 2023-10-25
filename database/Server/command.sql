CREATE TABLE
  `command` (
    `time` timestamp NOT NULL DEFAULT current_timestamp(),
    `name` varchar(255) NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL DEFAULT '474935164451946506',
    `bot_id` varchar(19) NOT NULL DEFAULT '938487470339801169',
    `args` longtext DEFAULT 'NULL',
    PRIMARY KEY (`time`, `name`),
    KEY `command_bot_relation` (`bot_id`),
    KEY `command_guild_relation` (`guild_id`),
    CONSTRAINT `command_bot_relation` FOREIGN KEY (`bot_id`) REFERENCES `bots` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `command_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1