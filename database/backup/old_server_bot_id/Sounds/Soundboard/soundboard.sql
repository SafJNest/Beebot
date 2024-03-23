CREATE TABLE
  `soundboard` (
    `id` smallint(6) NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `guild_id` varchar(19) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `soundboard_guild_relation` (`guild_id`),
    CONSTRAINT `soundboard_guild_relation` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  ) ENGINE = InnoDB AUTO_INCREMENT = 16 DEFAULT CHARSET = latin1