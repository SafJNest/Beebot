CREATE TABLE
  `channel` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `guild_id` varchar(19) NOT NULL,
    `bot_id` varchar(19) NOT NULL,
    `channel_id` varchar(19) NOT NULL,
    `exp_modifier` double NOT NULL DEFAULT 1,
    `exp_enabled` tinyint(4) NOT NULL DEFAULT 1,
    `stats_enabled` tinyint(4) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = latin1