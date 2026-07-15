ALTER TABLE `rank`
    ADD COLUMN `mmr` int(11) NOT NULL DEFAULT -1 AFTER `lp`,
    ADD KEY `rank_mmr_global` (`queue`,`mmr`,`wins`,`losses`,`summoner_id`),
    ADD KEY `rank_mmr_region` (`queue`,`region`,`mmr`,`wins`,`losses`,`summoner_id`);
