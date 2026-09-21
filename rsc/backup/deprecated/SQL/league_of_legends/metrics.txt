CREATE TABLE `metrics` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `league_shard` int(11) NOT NULL,
 `type` varchar(50) NOT NULL,
 `rank` varchar(255) DEFAULT NULL,
 `value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`value`)),
 PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=138 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
