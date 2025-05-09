CREATE TABLE
  `twitch_subscription` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `guild_id` varchar(19) NOT NULL,
    `streamer_id` varchar(32) NOT NULL,
    `channel_id` varchar(19) NOT NULL,
    `message` text DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `twitch_subscription_relation_1` (`guild_id`),
    CONSTRAINT `twitch_subscription_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 6 DEFAULT CHARSET = latin1