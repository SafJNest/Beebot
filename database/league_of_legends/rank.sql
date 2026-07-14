CREATE TABLE `rank` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `summoner_id` int(11) DEFAULT NULL,
 `queue` varchar(255) DEFAULT NULL,
 `rank` varchar(255) DEFAULT NULL,
 `lp` int(11) NOT NULL,
 `wins` int(11) NOT NULL,
 `losses` int(11) NOT NULL,
 `last_update` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
 PRIMARY KEY (`id`),
 UNIQUE KEY `summoner_id` (`summoner_id`,`queue`) USING BTREE,
 KEY `lp` (`lp`),
 CONSTRAINT `rank_summoner_id_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=72516220 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
