CREATE TABLE
  `sound_history` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `sound_id` int(10) unsigned NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `guild_id` varchar(19) DEFAULT NULL,
    `time` timestamp NOT NULL DEFAULT current_timestamp(),
    `source` tinyint(4) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `sound_id` (`sound_id`),
    KEY `user_id` (`user_id`),
    KEY `guild_id` (`guild_id`),
    KEY `source` (`source`),
    KEY `sound_history_sound_time` (`sound_id`, `time`),
    KEY `sound_history_guild_time_sound` (`guild_id`, `time`, `sound_id`),
    KEY `sound_history_user_sound_time` (`user_id`, `sound_id`, `time`),
    CONSTRAINT `sound_reproductions_relation_1` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 9 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin
