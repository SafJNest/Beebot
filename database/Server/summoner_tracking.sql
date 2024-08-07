CREATE TABLE
  `summoner_tracking` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `account_id` varchar(255) NOT NULL,
    `game_id` varchar(255) NOT NULL,
    `league_shard` tinyint(3) NOT NULL DEFAULT 3,
    `win` tinyint(1) NOT NULL,
    `rank` int(11) NOT NULL,
    `lp` int(11) NOT NULL,
    `gain` int(11) NOT NULL,
    `champion` int(11) NOT NULL DEFAULT 0,
    `time_start` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    `time_end` timestamp NOT NULL DEFAULT current_timestamp(),
    `patch` varchar(32) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `account_id_idx` (`account_id`),
    KEY `game_id_idx` (`game_id`),
    KEY `account_game_idx` (`account_id`, `game_id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 99 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci