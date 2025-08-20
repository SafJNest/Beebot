CREATE TABLE `album_artists` (
 `album_id` varchar(22) NOT NULL,
 `artist_id` varchar(22) NOT NULL,
 PRIMARY KEY (`album_id`,`artist_id`),
 KEY `artist_id` (`artist_id`),
 CONSTRAINT `album_artists_ibfk_1` FOREIGN KEY (`album_id`) REFERENCES `albums` (`album_id`) ON DELETE CASCADE ON UPDATE CASCADE,
 CONSTRAINT `album_artists_ibfk_2` FOREIGN KEY (`artist_id`) REFERENCES `artists` (`artist_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci