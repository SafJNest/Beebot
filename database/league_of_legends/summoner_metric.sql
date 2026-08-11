CREATE TABLE `summoner_metric` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `summoner_id` int(11) NOT NULL,
 `champion` int(11) NOT NULL,
 `score` int(11) NOT NULL DEFAULT 0,
 `games` smallint(11) NOT NULL,
 `wins` smallint(11) NOT NULL,
 `losses` smallint(11) NOT NULL,
 `kills` mediumint(11) NOT NULL,
 `deaths` mediumint(11) NOT NULL,
 `assists` mediumint(11) NOT NULL,
 `lp` int(11) NOT NULL,
 PRIMARY KEY (`id`),
 UNIQUE KEY `summoner_id` (`summoner_id`,`champion`),
 CONSTRAINT `summoner_summoner_id` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1192745 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
