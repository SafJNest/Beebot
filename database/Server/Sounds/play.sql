CREATE TABLE
  `play` (
    `user_id` varchar(19) NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    `times` smallint(6) NOT NULL,
    PRIMARY KEY (`user_id`, `sound_id`),
    KEY `play_relation_2` (`sound_id`),
    CONSTRAINT `play_relation_2` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1