DELETE legacy
FROM `rank` legacy
INNER JOIN `rank` canonical
    ON canonical.summoner_id = legacy.summoner_id
   AND canonical.queue = 'RANKED_SOLO_5X5'
WHERE legacy.queue = 'TEAM_BUILDER_RANKED_SOLO';

UPDATE `rank`
SET queue = 'RANKED_SOLO_5X5'
WHERE queue = 'TEAM_BUILDER_RANKED_SOLO';

ALTER TABLE `rank`
    DROP KEY `rank_region_filter`,
    DROP KEY `rank_mmr_global`,
    DROP KEY `rank_mmr_region`,
    DROP KEY `rank_mmr_filter`,
    ADD KEY `rank_mmr_global` (`queue`,`mmr`),
    ADD KEY `rank_mmr_region` (`queue`,`region`,`mmr`),
    ADD KEY `rank_mmr_filter` (`queue`,`rank`,`mmr`),
    ADD KEY `rank_mmr_region_filter` (`queue`,`region`,`rank`,`mmr`);
