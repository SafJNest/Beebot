CREATE TABLE `profile_statistics` (
 `key` varchar(255) NOT NULL,
 `summoner_id` int(11) NOT NULL,
 `time_start` datetime(3) NOT NULL,
 `time_end` datetime(3) NOT NULL,
 `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
 PRIMARY KEY (`key`),
 UNIQUE KEY `profile_statistics_summoner_time` (`summoner_id`,`time_start`),
 KEY `profile_statistics_time_end` (`time_end`),
 CONSTRAINT `profile_statistics_summoner_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
