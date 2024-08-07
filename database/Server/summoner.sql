CREATE TABLE
  `summoner` (
    `user_id` varchar(19) NOT NULL,
    `summoner_id` varchar(255) NOT NULL,
    `account_id` varchar(255) NOT NULL,
    `league_shard` tinyint(4) NOT NULL DEFAULT 3,
    `tracking` tinyint(4) NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `summoner_id`),
    KEY `sum_id_idx` (`summoner_id`),
    KEY `account_id_idx` (`account_id`),
    KEY `tracking_idx` (`tracking`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1 COLLATE = latin1_swedish_ci