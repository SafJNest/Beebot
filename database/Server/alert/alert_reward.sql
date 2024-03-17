CREATE TABLE
  `alert_reward` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `alert_id` int(10) unsigned NOT NULL,
    `level` tinyint(4) NOT NULL,
    `temporary` tinyint(1) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `alert_reward_relation_1` (`alert_id`),
    CONSTRAINT `alert_reward_relation_1` FOREIGN KEY (`alert_id`) REFERENCES `alert` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1