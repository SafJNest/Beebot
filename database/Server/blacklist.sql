CREATE TABLE
  `blacklist` (
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1