CREATE TABLE
  `lol_user` (
    `user_id` varchar(19) NOT NULL,
    `summoner_id` varchar(255) NOT NULL,
    `account_id` varchar(255) NOT NULL,
    PRIMARY KEY (`user_id`, `summoner_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1