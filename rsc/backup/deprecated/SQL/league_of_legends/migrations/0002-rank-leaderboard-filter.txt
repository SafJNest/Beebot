ALTER TABLE `rank`
    ADD KEY `rank_leaderboard_filter` (`queue`, `rank`, `lp`, `summoner_id`);
