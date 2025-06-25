CREATE TABLE
  `masteries` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `summoner_id` int(11) NOT NULL,
    `champion_id` int(11) NOT NULL,
    `champion_level` int(11) NOT NULL,
    `champion_points` int(11) NOT NULL,
    `last_play_time` datetime(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_summoner_champion` (`summoner_id`, `champion_id`),
    KEY `champion_level` (`champion_level`),
    KEY `champion_points` (`champion_points`),
    KEY `champion_id` (`champion_id`),
    KEY `last_play_time` (`last_play_time`),
    CONSTRAINT `masteries_summoner_id_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 21767191 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci