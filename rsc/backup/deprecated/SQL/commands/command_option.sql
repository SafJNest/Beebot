CREATE TABLE
  `command_option` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `command_id` int(10) unsigned NOT NULL,
    `key` varchar(255) NOT NULL,
    `description` varchar(255) NOT NULL,
    `required` tinyint(4) NOT NULL,
    `type` tinyint(4) NOT NULL,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 3 DEFAULT CHARSET = latin1