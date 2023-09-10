CREATE TABLE
  `soundboard_list` (
    `id` int(10) unsigned NOT NULL,
    `sound_id` smallint(6) NOT NULL,
    PRIMARY KEY (`id`, `sound_id`),
    KEY `soundboard_list_relation_2` (`sound_id`),
    CONSTRAINT `soundboard_list_relation_1` FOREIGN KEY (`id`) REFERENCES `soundboard` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT `soundboard_list_relation_2` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1