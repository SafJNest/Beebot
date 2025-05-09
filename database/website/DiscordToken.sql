CREATE TABLE `DiscordToken` (
 `accessToken` varchar(191) NOT NULL,
 `refreshToken` varchar(191) NOT NULL,
 `expiresAt` datetime(3) NOT NULL,
 PRIMARY KEY (`accessToken`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci