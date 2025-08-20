CREATE TABLE `artists` (
 `artist_id` varchar(22) NOT NULL,
 `name` varchar(255) NOT NULL,
 `image_url` varchar(255) DEFAULT NULL,
 PRIMARY KEY (`artist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci