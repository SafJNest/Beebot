CREATE TABLE
  `alert_role` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `alert_id` int(10) unsigned NOT NULL,
    `role_id` varchar(19) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `alert_role_relation_1` (`alert_id`),
    CONSTRAINT `alert_role_relation_1` FOREIGN KEY (`alert_id`) REFERENCES `alert` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 84 DEFAULT CHARSET = latin1