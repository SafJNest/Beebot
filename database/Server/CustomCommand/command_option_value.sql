CREATE TABLE
  `command_option_value` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `option_id` int(10) unsigned NOT NULL,
    `key` varchar(255) NOT NULL,
    `value` varchar(255) NOT NULL,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1