CREATE TABLE `track_streamings` (
 `streaming_id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
 `user_id` varchar(19) DEFAULT NULL,
 `track_id` varchar(22) DEFAULT NULL,
 `streamed_at` timestamp NOT NULL,
 `duration_ms` int(11) NOT NULL,
 PRIMARY KEY (`streaming_id`),
 UNIQUE KEY `streamed_at` (`streamed_at`,`user_id`,`track_id`),
 KEY `track_id` (`track_id`),
 KEY `duration_ms` (`duration_ms`),
 KEY `idx_user_duration_track` (`user_id`,`duration_ms`,`track_id`),
 KEY `idx_user_duration_track_full` (`user_id`,`duration_ms`,`track_id`,`streamed_at`),
 CONSTRAINT `track_streamings_ibfk_1` FOREIGN KEY (`track_id`) REFERENCES `tracks` (`track_id`) ON DELETE CASCADE ON UPDATE CASCADE,
 CONSTRAINT `track_streamings_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`discord_user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1048827 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci