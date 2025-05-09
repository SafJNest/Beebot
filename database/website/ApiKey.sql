CREATE TABLE `ApiKey` (
 `id` varchar(191) NOT NULL,
 `hashedKey` varchar(191) NOT NULL,
 `sha256Hash` varchar(191) NOT NULL,
 `userId` varchar(191) NOT NULL,
 `createdAt` datetime(3) NOT NULL DEFAULT current_timestamp(3),
 `active` tinyint(1) NOT NULL DEFAULT 1,
 PRIMARY KEY (`id`),
 UNIQUE KEY `ApiKey_sha256Hash_key` (`sha256Hash`),
 KEY `ApiKey_userId_fkey` (`userId`),
 CONSTRAINT `ApiKey_userId_fkey` FOREIGN KEY (`userId`) REFERENCES `User` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci