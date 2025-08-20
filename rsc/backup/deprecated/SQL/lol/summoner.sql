CREATE TABLE
  `summoner` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `riot_id` varchar(255) DEFAULT NULL,
    `summoner_id` varchar(255) NOT NULL,
    `account_id` varchar(255) DEFAULT NULL,
    `puuid` varchar(255) NOT NULL,
    `league_shard` tinyint(4) NOT NULL DEFAULT 3,
    `user_id` varchar(19) DEFAULT NULL,
    `tracking` tinyint(4) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_puuid_idx` (`puuid`, `league_shard`),
    UNIQUE KEY `unique_account_shard` (`account_id`, `league_shard`),
    FULLTEXT KEY `idx_riot_id` (`riot_id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 24627 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci