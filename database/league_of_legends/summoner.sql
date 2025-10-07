CREATE TABLE `summoner` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `riot_id` varchar(191) DEFAULT NULL,
 `level` int(11) DEFAULT NULL,
 `icon` int(11) DEFAULT NULL,
 `region` varchar(255) DEFAULT NULL,
 `puuid` varchar(191) NOT NULL,
 `user_id` varchar(191) DEFAULT NULL,
 `ban` tinyint(4) NOT NULL DEFAULT 0,
 `tracking` int(11) NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`),
 UNIQUE KEY `unique_puuid_idx` (`puuid`,`region`) USING BTREE,
 KEY `user_idx` (`user_id`),
 KEY `tracking` (`tracking`),
 KEY `level` (`level`),
 KEY `icon` (`icon`),
 FULLTEXT KEY `idx_riot_id` (`riot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci