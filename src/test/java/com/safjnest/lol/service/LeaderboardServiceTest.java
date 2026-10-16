package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.utils.TierDivisionUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class LeaderboardServiceTest {

    @Test
    public void absoluteRankingAddsAllHigherExactRanksAndTheOneBasedSegmentPosition() {
        Map<TierDivisionType, Long> counts = Map.of(
            TierDivisionType.CHALLENGER_I, 2L,
            TierDivisionType.GRANDMASTER_I, 3L,
            TierDivisionType.MASTER_I, 5L,
            TierDivisionType.GOLD_I, 7L,
            TierDivisionType.GOLD_II, 11L,
            TierDivisionType.GOLD_III, 13L,
            TierDivisionType.GOLD_IV, 17L
        );

        assertEquals(Long.valueOf(42), LeaderboardService.absoluteRanking(counts, TierDivisionType.GOLD_IV, 0L));
        assertEquals(Long.valueOf(58), LeaderboardService.absoluteRanking(counts, TierDivisionType.GOLD_IV, 16L));
        assertNull(LeaderboardService.absoluteRanking(counts, TierDivisionType.SILVER_IV, 0L));
    }

    @Test
    public void onlyMasterAndAboveSegmentsArePermanent() {
        assertTrue(TierDivisionUtils.isHighElo(TierDivisionType.MASTER_I));
        assertTrue(TierDivisionUtils.isHighElo(TierDivisionType.GRANDMASTER_I));
        assertTrue(TierDivisionUtils.isHighElo(TierDivisionType.CHALLENGER_I));
        assertFalse(TierDivisionUtils.isHighElo(TierDivisionType.DIAMOND_I));
    }

    @Test
    public void summonerPositionCombinesTierDivisionsAndFindsDeepPages() {
        Map<TierDivisionType, Long> goldCounts = Map.of(
            TierDivisionType.GOLD_I, 3L,
            TierDivisionType.GOLD_II, 4L,
            TierDivisionType.GOLD_III, 5L,
            TierDivisionType.GOLD_IV, 6L
        );

        long goldPosition = LeaderboardService.tierPositionBefore(goldCounts, TierDivisionType.GOLD_IV, 7);
        assertEquals(19L, goldPosition);
        assertEquals(1, LeaderboardService.pageForPosition(goldPosition, 50));
        assertEquals(3_601, LeaderboardService.pageForPosition(180_000, 50));
    }

    @Test
    public void summonerPositionWithinSingleDivisionUsesItsRedisOrdinal() {
        assertEquals(120L, LeaderboardService.tierPositionBefore(
            Map.of(TierDivisionType.MASTER_I, 1_000L), TierDivisionType.MASTER_I, 120));
        assertEquals(4, LeaderboardService.pageForPosition(150, 50));
    }

    @Test
    public void summonerMetadataReportsEffectiveFiltersAndComputedPagination() {
        LeaderboardPage page = new LeaderboardPage(24, 2, 70, 35, List.of());

        var metadata = LeaderboardService.summonerMetadata(
            page, GameQueueType.RANKED_SOLO_5X5, TierType.GOLD, LeagueShard.EUW1);

        assertEquals(Integer.valueOf(24), metadata.pagination().page());
        assertEquals(Integer.valueOf(2), metadata.pagination().pageSize());
        assertEquals(Integer.valueOf(2), metadata.pagination().limit());
        assertEquals(Integer.valueOf(46), metadata.pagination().offset());
        assertEquals(Long.valueOf(70), metadata.pagination().total());
        assertEquals(Long.valueOf(35), metadata.pagination().pages());
        assertTrue(metadata.pagination().hasMore());
        assertEquals(GameQueueType.RANKED_SOLO_5X5, metadata.filter().queue());
        assertEquals(TierType.GOLD, metadata.filter().rank());
        assertEquals(Filter.RankBehavior.EXACT, metadata.filter().rankBehavior());
        assertEquals(LeagueShard.EUW1, metadata.filter().region());
        assertNull(metadata.filter().lane());
        assertNull(metadata.filter().patch());
    }

    @Test
    public void rankKeepsContextualPositionsOutOfTheUnrankedDefault() {
        Rank rank = new Rank(TierDivisionType.GOLD_IV, 0, 6, 7).withRankings(3_163_321L, 742_918L);

        assertEquals(Long.valueOf(3_163_321L), rank.globalRanking());
        assertEquals(Long.valueOf(742_918L), rank.regionRanking());
        assertNull(Rank.unranked().globalRanking());
        assertNull(Rank.unranked().regionRanking());
    }
}
