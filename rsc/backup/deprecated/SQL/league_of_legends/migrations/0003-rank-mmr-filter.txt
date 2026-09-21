ALTER TABLE `rank`
    DROP KEY `rank_leaderboard_filter`,
    ADD KEY `rank_mmr_filter` (`queue`,`rank`,`mmr`,`wins`,`losses`,`summoner_id`);
