CREATE TABLE `leaderboard_distribution` (
 `queue` varchar(255) NOT NULL,
 `rank` varchar(32) NOT NULL,
 `region` varchar(255) NOT NULL,
 `players` bigint(20) unsigned NOT NULL,
 `updated_at` datetime(3) NOT NULL,
 PRIMARY KEY (`queue`,`rank`,`region`),
 KEY `leaderboard_distribution_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
