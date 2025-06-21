CREATE TABLE
  `play` (
    `user_id` varchar(19) NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    `times` smallint(6) NOT NULL,
    PRIMARY KEY (`user_id`, `sound_id`),
    KEY `play_sound_relation` (`sound_id`),
    CONSTRAINT `play_sound_relation` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1