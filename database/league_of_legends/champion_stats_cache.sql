CREATE TABLE IF NOT EXISTS `champion_stats_cache` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `summoner_id` INT NOT NULL,
  `champion_id` SMALLINT NOT NULL,
  `queue_type` VARCHAR(50),
  `lane` VARCHAR(20),
  `rank_tier` VARCHAR(20),
  `time_period_start` DATETIME,
  `time_period_end` DATETIME,
  
  -- Aggregated Stats
  `games` INT DEFAULT 0,
  `wins` INT DEFAULT 0,
  `losses` INT DEFAULT 0,
  `avg_kda` DECIMAL(5,2) DEFAULT 0,
  `avg_cs` DECIMAL(6,2) DEFAULT 0,
  `avg_csm` DECIMAL(5,2) DEFAULT 0,
  `avg_gpm` DECIMAL(7,2) DEFAULT 0,
  `avg_dpm` DECIMAL(7,2) DEFAULT 0,
  `avg_vspm` DECIMAL(5,2) DEFAULT 0,
  `avg_kp` DECIMAL(5,2) DEFAULT 0,
  
  -- Metadata
  `last_calculated` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `cache_version` INT DEFAULT 1,
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_stats` (`summoner_id`, `champion_id`, `queue_type`, `lane`, `rank_tier`, `time_period_start`, `time_period_end`),
  KEY `idx_summoner` (`summoner_id`),
  KEY `idx_champion` (`champion_id`),
  KEY `idx_last_calc` (`last_calculated`),
  CONSTRAINT `champion_stats_cache_summoner_id_fkey` FOREIGN KEY (`summoner_id`) REFERENCES `summoner` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
