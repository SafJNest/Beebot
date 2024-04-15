CREATE TABLE
  `command_task_message` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `task_value_id` int(10) unsigned NOT NULL,
    `message` text NOT NULL,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 15 DEFAULT CHARSET = latin1