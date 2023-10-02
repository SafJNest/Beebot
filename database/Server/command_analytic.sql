CREATE TABLE
  `command_analytic` (
    `time` timestamp NOT NULL DEFAULT current_timestamp(),
    `name` varchar(255) NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) DEFAULT '474935164451946506',
    `bot_id` varchar(19) DEFAULT '938487470339801169',
    `args` varchar(2048) DEFAULT 'NULL',
    PRIMARY KEY (`time`, `name`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1