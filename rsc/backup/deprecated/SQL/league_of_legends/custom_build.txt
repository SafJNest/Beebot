CREATE TABLE `custom_build` (
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `name` varchar(255) NOT NULL,
 `description` text NOT NULL,
 `user_id` varchar(19) NOT NULL,
 `build` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`build`)),
 `champion` smallint(6) NOT NULL,
 `skin` tinyint(4) NOT NULL DEFAULT 0,
 `lane` tinyint(4) NOT NULL,
 `created_at` datetime NOT NULL DEFAULT current_timestamp(),
 PRIMARY KEY (`id`),
 KEY `champidx` (`champion`),
 KEY `user_idx` (`user_id`),
 KEY `lane` (`lane`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
