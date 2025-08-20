CREATE TABLE
  `sound_interactions` (
    `sound_id` int(10) unsigned NOT NULL,
    `user_id` varchar(19) NOT NULL,
    `value` tinyint(4) NOT NULL DEFAULT 0,
    PRIMARY KEY (`sound_id`, `user_id`),
    KEY `value` (`value`),
    CONSTRAINT `sound_interactions_relation_1` FOREIGN KEY (`sound_id`) REFERENCES `sound` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin