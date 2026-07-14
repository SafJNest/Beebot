CREATE TABLE `champion_stats` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `filter` varchar(255) NOT NULL,
 `champion` int(11) NOT NULL,
 `data` longtext NOT NULL,
 PRIMARY KEY (`id`),
 UNIQUE KEY `filter_2` (`filter`,`champion`) USING BTREE,
 KEY `filter` (`filter`),
 KEY `champion` (`champion`)
) ENGINE=InnoDB AUTO_INCREMENT=95634 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
