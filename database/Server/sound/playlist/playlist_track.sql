CREATE TABLE
  `playlist_track` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `playlist_id` int(10) unsigned NOT NULL,
    `uri` varchar(255) NOT NULL,
    `encoded_track` varchar(1023) NOT NULL,
    `added_at` timestamp NOT NULL DEFAULT current_timestamp(),
    `order` int(10) unsigned NOT NULL,
    PRIMARY KEY (`id`),
    KEY `playlist_track_relation_1` (`playlist_id`),
    CONSTRAINT `playlist_track_relation_1` FOREIGN KEY (`playlist_id`) REFERENCES `playlist` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 35240 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin