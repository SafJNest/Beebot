CREATE TABLE `automated_action_expiration` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `member_id` int(11) NOT NULL,
 `action_id` int(11) NOT NULL,
 `time` timestamp NOT NULL,
 PRIMARY KEY (`id`),
 KEY `automated_action_expiration_relation_1` (`action_id`),
 KEY `automated_action_expiration_relation_2` (`member_id`),
 CONSTRAINT `automated_action_expiration_relation_1` FOREIGN KEY (`action_id`) REFERENCES `automated_action` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
 CONSTRAINT `automated_action_expiration_relation_2` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci