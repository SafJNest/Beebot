CREATE TABLE
  `tag_sounds` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `sound_id` smallint(6) NOT NULL,
    `tag_id` int(10) unsigned NOT NULL,
    PRIMARY KEY (`id`),
    KEY `tag_sounds_relation_1` (`sound_id`),
    KEY `tag_sounds_relation_2` (`tag_id`),
    KEY `idx_tag_sounds_sound_id_tag_id` (`sound_id`, `tag_id`),
    CONSTRAINT `tag_sounds_relation_1` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `tag_sounds_relation_2` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 141 DEFAULT CHARSET = latin1