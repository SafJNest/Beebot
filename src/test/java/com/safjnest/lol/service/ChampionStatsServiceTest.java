package com.safjnest.lol.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.champion.ChampionStatsData;
import com.safjnest.lol.model.Filter;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ChampionStatsServiceTest {

    @Test
    public void greaterOrEqualMatrixBucketIncludesHigherRanks() {
        ChampionStatsData.RawMatch match = rawMatch(LeagueShard.EUW1, TierType.CHALLENGER);
        Filter emerald = filter(LeagueShard.EUW1, TierType.EMERALD);
        Filter challenger = filter(LeagueShard.EUW1, TierType.CHALLENGER);

        assertTrue(ChampionStatsService.matchesMatrixFilter(emerald, match));
        assertTrue(ChampionStatsService.matchesMatrixFilter(challenger, match));
    }

    @Test
    public void matrixBucketRejectsLowerRanksAndOtherRegions() {
        ChampionStatsData.RawMatch goldMatch = rawMatch(LeagueShard.EUW1, TierType.GOLD);
        Filter diamond = filter(LeagueShard.EUW1, TierType.DIAMOND);
        Filter euw = filter(LeagueShard.EUW1, TierType.EMERALD);
        Filter na = filter(LeagueShard.NA1, TierType.EMERALD);

        assertFalse(ChampionStatsService.matchesMatrixFilter(diamond, goldMatch));
        assertTrue(ChampionStatsService.matchesMatrixFilter(euw, goldMatch));
        assertFalse(ChampionStatsService.matchesMatrixFilter(na, goldMatch));
    }

    @Test
    public void exactRankBucketDoesNotAccumulateHigherRanks() {
        ChampionStatsData.RawMatch match = rawMatch(LeagueShard.EUW1, TierType.CHALLENGER);
        Filter exactMaster = filter(LeagueShard.EUW1, TierType.MASTER)
            .setRankBehavior(Filter.RankBehavior.EXACT);

        assertFalse(ChampionStatsService.matchesMatrixFilter(exactMaster, match));
    }

    private static Filter filter(LeagueShard region, TierType rank) {
        return new Filter()
            .setPatch("15.14")
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setRegion(region)
            .setRank(rank);
    }

    private static ChampionStatsData.RawMatch rawMatch(LeagueShard region, TierType rank) {
        return new ChampionStatsData.RawMatch(
            "EUW1_1",
            new ChampionStatsData.MatchMeta(Map.of(), Map.of(), 1, 2, region, rank),
            List.of());
    }
}
