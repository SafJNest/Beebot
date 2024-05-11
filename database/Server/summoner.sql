CREATE TABLE
  `summoner` (
    `user_id` varchar(19) NOT NULL,
    `summoner_id` varchar(255) NOT NULL,
    `account_id` varchar(255) NOT NULL,
    `league_shard` tinyint(4) NOT NULL DEFAULT 3,
    PRIMARY KEY (`user_id`, `summoner_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1