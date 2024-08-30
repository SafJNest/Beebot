CREATE TABLE
  `sound_interactions` (
    `user_id` varchar(19) NOT NULL,
    `sound_id` int(10) unsigned NOT NULL,
    `times` int(11) NOT NULL DEFAULT 0,
    `like` tinyint(1) NOT NULL DEFAULT 0,
    `dislike` tinyint(1) NOT NULL DEFAULT 0,
    `last_play` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    PRIMARY KEY (`user_id`, `sound_id`),
    KEY `play_sound_relation` (`sound_id`),
    CONSTRAINT `play_relation_1` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1 COLLATE = latin1_swedish_ci