package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
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
            (LeagueShardUtils.getActives().size() + 1)
                * (TierDivisionUtils.getHigherTiers(TierType.IRON).size() + 1)
                * (LaneTypeUtils.playables().size() + 1),
            filters.size());
        boolean hasGlobal = false;
        boolean hasTop = false;
        for (Filter filter : filters) {
            assertEquals("15.14", filter.patch());
            assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, filter.queue());
            assertEquals(0, filter.champion());
            hasGlobal |= filter.rank() == null && filter.region() == null && filter.lane() == null;
            hasTop |= filter.lane() != null;
            keys.add(filter.genericKey());
        }
        assertEquals(filters.size(), keys.size());
        assertTrue(hasGlobal);
        assertTrue(hasTop);
    }

    @Test
    public void matrixDoesNotCreateRoleDimensionForLaneLessQueues() {
        Filter filter = ChampionDataRefreshService.matrixFilters(
                "15.14", GameQueueType.ARAM).get(0);

        assertNull(filter.lane());
        assertEquals((LeagueShardUtils.getActives().size() + 1)
                * (TierDivisionUtils.getHigherTiers(TierType.IRON).size() + 1),
            ChampionDataRefreshService.matrixFilters("15.14", GameQueueType.ARAM).size());
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
