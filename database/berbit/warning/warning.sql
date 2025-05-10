CREATE TABLE `warning` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `member_id` int(11) NOT NULL,
 `reason` varchar(255) NOT NULL,
 `time` timestamp NOT NULL DEFAULT current_timestamp(),
 PRIMARY KEY (`id`),
 KEY `warning_relation_1` (`member_id`),
 CONSTRAINT `warning_relation_1` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=94 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin