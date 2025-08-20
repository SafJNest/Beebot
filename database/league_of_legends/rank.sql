CREATE TABLE
  `rank` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `summoner_id` int(11) NOT NULL,
    `game_type` int(11) NOT NULL,
    `rank` int(11) NOT NULL,
    `lp` int(11) NOT NULL,
    `wins` int(11) NOT NULL,
    `losses` int(11) NOT NULL,
    `last_update` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_summoner_rank` (`summoner_id`),
    UNIQUE KEY `summoner_id` (`summoner_id`, `game_type`),
    KEY `rank_idx` (`rank`),
    KEY `lp_idx` (`lp`),
    KEY `update_idx` (`last_update`),
    KEY `wins` (`wins`),
    KEY `losses` (`losses`),
    KEY `game_type` (`game_type`),
    CONSTRAINT `rank_summoner_id_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 292085 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci