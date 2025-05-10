CREATE TABLE
  `summoner_match` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `game_id` varchar(255) NOT NULL,
    `league_shard` tinyint(4) NOT NULL,
    `game_type` tinyint(4) NOT NULL,
    `time_start` timestamp NOT NULL,
    `time_end` timestamp NOT NULL,
    `patch` varchar(255) NOT NULL,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 8563 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin