CREATE TABLE
  `summoner_tracking` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `account_id` varchar(255) NOT NULL,
    `summoner_match_id` int(11) NOT NULL,
    `win` tinyint(1) NOT NULL,
    `kda` varchar(32) NOT NULL DEFAULT '',
    `rank` int(11) NOT NULL,
    `lp` int(11) NOT NULL,
    `gain` int(11) NOT NULL,
    `champion` int(11) NOT NULL DEFAULT 0,
    `lane` tinyint(4) DEFAULT NULL,
    `side` tinyint(4) NOT NULL,
    `build` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '{}' CHECK (json_valid(`build`)),
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_game_analyzed` (`account_id`, `summoner_match_id`),
    KEY `account_id_idx` (`account_id`),
    KEY `rank_idx` (`rank`),
    KEY `lane_idx` (`lane`),
    KEY `side_idx` (`side`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 7166 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci