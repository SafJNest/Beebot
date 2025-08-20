CREATE TABLE `tracks` (
 `track_id` varchar(22) NOT NULL,
 `title` varchar(255) NOT NULL,
 `album_id` varchar(22) DEFAULT NULL,
 PRIMARY KEY (`track_id`),
 KEY `album_id` (`album_id`),
 KEY `track_id` (`track_id`),
 CONSTRAINT `tracks_ibfk_1` FOREIGN KEY (`album_id`) REFERENCES `albums` (`album_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci