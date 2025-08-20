CREATE TABLE
  `sound_history` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `sound_id` int(10) unsigned NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `time` timestamp NOT NULL DEFAULT current_timestamp(),
    `source` tinyint(4) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `sound_id` (`sound_id`),
    KEY `user_id` (`user_id`),
    KEY `source` (`source`),
    CONSTRAINT `sound_reproductions_relation_1` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 9 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin