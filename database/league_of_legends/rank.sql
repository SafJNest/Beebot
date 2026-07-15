CREATE TABLE `rank` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `summoner_id` int(11) DEFAULT NULL,
 `region` varchar(255) DEFAULT NULL,
 `queue` varchar(255) DEFAULT NULL,
 `rank` varchar(255) DEFAULT NULL,
 `lp` int(11) NOT NULL,
 `wins` int(11) NOT NULL,
 `losses` int(11) NOT NULL,
 `last_update` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
 PRIMARY KEY (`id`),
 UNIQUE KEY `unique_summoner_rank` (`summoner_id`),
 UNIQUE KEY `summoner_id` (`summoner_id`,`queue`) USING BTREE,
 KEY `lp_idx` (`lp`),
 KEY `update_idx` (`last_update`),
 KEY `game_type` (`queue`) USING BTREE,
 KEY `rank_region_filter` (`region`,`queue`,`rank`,`summoner_id`),
 CONSTRAINT `rank_summoner_id_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
