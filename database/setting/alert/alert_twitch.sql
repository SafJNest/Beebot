CREATE TABLE
  `alert_twitch` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `alert_id` int(10) unsigned NOT NULL,
    `streamer_id` varchar(32) NOT NULL,
    `role_id` varchar(19) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `alert_reward_relation_1` (`alert_id`),
    CONSTRAINT `alert_twitch_relation_1` FOREIGN KEY (`alert_id`) REFERENCES `alert` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 34 DEFAULT CHARSET = latin1 COLLATE = latin1_swedish_ci