CREATE TABLE `albums` (
 `album_id` varchar(22) NOT NULL,
 `title` varchar(255) NOT NULL,
 `cover_art` varchar(255) DEFAULT NULL,
 PRIMARY KEY (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci