CREATE TABLE `UserSession` (
 `id` varchar(191) NOT NULL,
 `userId` varchar(191) NOT NULL,
 `discordTokenId` varchar(191) NOT NULL,
 `expiresAt` datetime(3) NOT NULL,
 `createdAt` datetime(3) NOT NULL DEFAULT current_timestamp(3),
 `updatedAt` datetime(3) NOT NULL,
 PRIMARY KEY (`id`),
 KEY `UserSession_userId_idx` (`userId`),
 KEY `UserSession_discordTokenId_fkey` (`discordTokenId`),
 CONSTRAINT `UserSession_discordTokenId_fkey` FOREIGN KEY (`discordTokenId`) REFERENCES `DiscordToken` (`accessToken`) ON DELETE CASCADE ON UPDATE CASCADE,
 CONSTRAINT `UserSession_userId_fkey` FOREIGN KEY (`userId`) REFERENCES `User` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci