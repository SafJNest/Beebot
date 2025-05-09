CREATE TABLE `automated_action` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `guild_id` varchar(19) NOT NULL,
 `action` tinyint(4) NOT NULL,
 `action_role` varchar(19) DEFAULT NULL,
 `action_time` int(11) DEFAULT NULL,
 `infractions` int(11) NOT NULL,
 `infractions_time` int(11) DEFAULT NULL,
 PRIMARY KEY (`id`),
 KEY `automated_action_relation_1` (`guild_id`),
 CONSTRAINT `automated_action_relation_1` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`guild_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci