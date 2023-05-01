CREATE TABLE
  `guild_settings` (
    `guild_id` varchar(19) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    `prefix` varchar(32) NOT NULL,
    PRIMARY KEY (`guild_id`, `bot_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1