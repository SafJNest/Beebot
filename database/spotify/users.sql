CREATE TABLE `users` (
 `discord_user_id` varchar(19) NOT NULL,
 `spotify_user_id` varchar(255) DEFAULT NULL,
 `username` varchar(255) NOT NULL,
 PRIMARY KEY (`discord_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci