CREATE TABLE
  `tag` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    FULLTEXT KEY `fulltextmae` (`name`)
  ) ENGINE = InnoDB AUTO_INCREMENT = 29 DEFAULT CHARSET = latin1