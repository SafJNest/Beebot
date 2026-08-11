CREATE TABLE `champion_builds` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `games` int(11) NOT NULL,
 `winrate` double NOT NULL,
 `filter` varchar(512) NOT NULL,
 `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
 `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
 PRIMARY KEY (`id`),
 KEY `filter` (`filter`),
 KEY `winrate` (`winrate`),
 KEY `updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=5686948 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
