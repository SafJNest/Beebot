CREATE TABLE
  `command_task_value` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `task_id` int(10) unsigned NOT NULL,
    `value` int(10) unsigned NOT NULL,
    `from_option` tinyint(4) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 18 DEFAULT CHARSET = latin1