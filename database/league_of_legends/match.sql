CREATE TABLE `match` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `game_id` varchar(191) NOT NULL,
 `league_shard` int(11) NOT NULL,
 `game_type` int(11) NOT NULL,
 `bans` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`bans`)),
 `time_start` datetime(3) NOT NULL,
 `time_end` datetime(3) NOT NULL,
 `patch` varchar(191) NOT NULL,
 PRIMARY KEY (`id`),
 UNIQUE KEY `match_game_id_key` (`game_id`),
 UNIQUE KEY `unique_game` (`game_id`,`league_shard`),
 KEY `game_type_idx` (`game_type`),
 KEY `time_start_idx` (`time_start`),
 KEY `time_end_idx` (`time_end`)
) ENGINE=InnoDB AUTO_INCREMENT=32768 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci