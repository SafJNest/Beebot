CREATE TABLE `masteries` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `summoner_id` int(11) NOT NULL,
 `champion_id` int(11) NOT NULL,
 `champion_level` int(11) NOT NULL,
 `champion_points` int(11) NOT NULL,
 `last_play_time` datetime(3) NOT NULL,
 PRIMARY KEY (`id`),
 UNIQUE KEY `unique_summoner_champion` (`summoner_id`,`champion_id`),
 CONSTRAINT `masteries_summoner_id_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=81953312 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
