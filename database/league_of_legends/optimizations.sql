-- Performance optimization indexes for advanced stats queries
-- These indexes improve query performance for filtering and aggregating player statistics

-- Participant table optimizations
-- Composite index for summoner + champion + lane queries (common in stats)
CREATE INDEX IF NOT EXISTS `idx_summoner_champion_lane` ON `participant`(`summoner_id`, `champion`, `lane`);

-- Index for rank-based filtering
CREATE INDEX IF NOT EXISTS `idx_summoner_rank` ON `participant`(`summoner_id`, `rank`);

-- Index for time-based queries with summoner
CREATE INDEX IF NOT EXISTS `idx_match_summoner` ON `participant`(`match_id`, `summoner_id`);

-- Match table optimizations  
-- Composite index for queue + time filtering
CREATE INDEX IF NOT EXISTS `idx_queue_time` ON `match`(`queue`, `time_start`);

-- Composite index for region + queue filtering
CREATE INDEX IF NOT EXISTS `idx_region_queue` ON `match`(`region`, `queue`);

-- Index for patch filtering
CREATE INDEX IF NOT EXISTS `idx_patch` ON `match`(`patch`);

-- Index for time_end for recent match queries
CREATE INDEX IF NOT EXISTS `idx_time_end` ON `match`(`time_end`);

-- Note: Run ANALYZE TABLE after creating indexes to update statistics
-- ANALYZE TABLE `participant`;
-- ANALYZE TABLE `match`;
