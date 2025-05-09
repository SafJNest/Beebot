CREATE TABLE
  `command_task` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `command_id` int(10) unsigned NOT NULL,
    `order` tinyint(4) NOT NULL,
    `type` tinyint(4) NOT NULL,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 22 DEFAULT CHARSET = latin1