CREATE TABLE
  `rooms_nickname` (
    `guild_id` varchar(19) NOT NULL,
    `room_id` varchar(19) NOT NULL,
    `room_name` varchar(255) DEFAULT NULL,
    PRIMARY KEY (`guild_id`, `room_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1