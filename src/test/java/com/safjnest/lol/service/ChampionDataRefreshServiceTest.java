package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ChampionDataRefreshServiceTest {

    @Test
    public void matrixUsesEveryActiveRegionAndRankThreshold() {
        List<Filter> filters = ChampionDataRefreshService.matrixFilters(
            "15.14", GameQueueType.TEAM_BUILDER_RANKED_SOLO);
        Set<String> keys = new HashSet<>();

        assertEquals(
            LeagueShardUtils.getActives().size() * TierDivisionUtils.getHigherTiers(TierType.IRON).size(),
            filters.size());
        for (Filter filter : filters) {
            assertEquals("15.14", filter.patch());
            assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, filter.queue());
            assertNull(filter.lane());
            assertNotNull(filter.rank());
            assertNotNull(filter.region());
            assertEquals(0, filter.champion());
            keys.add(filter.genericKey());
        }
        assertEquals(filters.size(), keys.size());
    }

    @Test
    public void matrixDoesNotCreateRoleDimensionForLaneLessQueues() {
        Filter filter = ChampionDataRefreshService.matrixFilters(
                "15.14", GameQueueType.ARAM).get(0);

        assertNull(filter.lane());
    }

    @Test
    public void matrixSkipsReadyFilterKeys() {
        List<Filter> combinations = ChampionDataRefreshService.matrixFilters(
            "15.14", GameQueueType.TEAM_BUILDER_RANKED_SOLO).subList(0, 3);
        Set<String> ready = Set.of(combinations.get(1).genericKey());

        List<Filter> missing = ChampionDataRefreshService.missingMatrixFilters(combinations, ready);

        assertEquals(2, missing.size());
        boolean skipped = false;
        for (Filter filter : missing) if (filter.genericKey().equals(combinations.get(1).genericKey())) skipped = true;
        assertFalse(skipped);
    }
}
