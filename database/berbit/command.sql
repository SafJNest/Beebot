CREATE TABLE
  `command` (
    `id` smallint(6) NOT NULL AUTO_INCREMENT,
    `time` timestamp NOT NULL DEFAULT current_timestamp(),
    `name` varchar(255) NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) NOT NULL DEFAULT '474935164451946506',
    `args` longtext DEFAULT 'NULL',
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 7695 DEFAULT CHARSET = latin1