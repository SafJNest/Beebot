CREATE TABLE
  `soundboard_sounds` (
    `id` smallint(6) NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    PRIMARY KEY (`id`, `sound_id`),
    KEY `soundboard_list_relation_2` (`sound_id`),
    CONSTRAINT `soundboard_sounds__sound_relation` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `soundboard_sounds_soundboard_relation` FOREIGN KEY (`id`) REFERENCES `soundboard` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1