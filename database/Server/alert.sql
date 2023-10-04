CREATE TABLE
  `alert` (
    `guild_id` varchar(19) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    `boost_channel` varchar(19) DEFAULT NULL,
    `boost_message` text DEFAULT NULL,
    `leave_channel` varchar(19) DEFAULT NULL,
    `leave_message` text DEFAULT NULL,
    `levelup_message` text DEFAULT NULL,
    `welcome_channel` varchar(19) DEFAULT NULL,
    `welcome_message` text DEFAULT NULL,
    PRIMARY KEY (`guild_id`, `bot_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1