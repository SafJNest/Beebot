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

public class ChampionServiceMatrixTest {

    @Test
    public void matrixUsesEveryActiveRegionAndRankThreshold() {
        List<Filter> filters = ChampionService.matrixFilters(
            "15.14", GameQueueType.TEAM_BUILDER_RANKED_SOLO);
        Set<String> keys = new HashSet<>();

        assertEquals(
            (LeagueShardUtils.getActives().size() + 1)
                * (TierDivisionUtils.getHigherTiers(TierType.IRON).size() + 1),
            filters.size());
        boolean hasGlobal = false;
        for (Filter filter : filters) {
            assertEquals("15.14", filter.patch());
            assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, filter.queue());
            assertEquals(0, filter.champion());
            hasGlobal |= filter.rank() == null && filter.region() == null && filter.lane() == null;
            assertNull(filter.lane());
            keys.add(filter.genericKey());
        }
        assertEquals(filters.size(), keys.size());
        assertTrue(hasGlobal);
    }

    @Test
    public void matrixDoesNotCreateRoleDimensionForLaneLessQueues() {
        Filter filter = ChampionService.matrixFilters(
                "15.14", GameQueueType.ARAM).get(0);

        assertNull(filter.lane());
        assertEquals((LeagueShardUtils.getActives().size() + 1)
                * (TierDivisionUtils.getHigherTiers(TierType.IRON).size() + 1),
            ChampionService.matrixFilters("15.14", GameQueueType.ARAM).size());
    }

    @Test
    public void matrixSkipsReadyFilterKeys() {
        List<Filter> combinations = ChampionService.matrixFilters(
            "15.14", GameQueueType.TEAM_BUILDER_RANKED_SOLO).subList(0, 3);
        String readyKey = com.safjnest.lol.model.statistics.shared.ChampionStatsScope.from(combinations.get(1)).toKey();
        Set<String> ready = Set.of(readyKey);

        List<Filter> missing = ChampionService.missingMatrixFilters(combinations, ready);

        assertEquals(2, missing.size());
        boolean skipped = false;
        for (Filter filter : missing) if (com.safjnest.lol.model.statistics.shared.ChampionStatsScope.from(filter).toKey().equals(readyKey)) skipped = true;
        assertFalse(skipped);
    }

    @Test
    public void statisticsFilterAndCachesPreserveRankBehavior() {
        Filter filter = new Filter().setChampion(10).setPatch("15.14").setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setRank(TierType.EMERALD).setRankBehavior(Filter.RankBehavior.EXACT).setPeriod(100, 200);

        Filter statistics = ChampionService.statisticsFilter(filter);
        List<Filter> cached = ChampionService.statisticsCacheFilters(filter);

        assertEquals(Filter.RankBehavior.EXACT, statistics.rankBehavior());
        assertEquals(7, cached.size());
        assertTrue(cached.stream().anyMatch(value -> value.lane() == null));
        assertTrue(cached.stream().anyMatch(value -> value.lane() == no.stelar7.api.r4j.basic.constants.types.lol.LaneType.NONE));
        for (Filter value : cached) {
            assertEquals(Filter.RankBehavior.EXACT, value.rankBehavior());
        }
    }
}
