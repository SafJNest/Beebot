package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.TierDivisionUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

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
    public void rankKeepsContextualPositionsOutOfTheUnrankedDefault() {
        Rank rank = new Rank(TierDivisionType.GOLD_IV, 0, 6, 7).withRankings(3_163_321L, 742_918L);

        assertEquals(Long.valueOf(3_163_321L), rank.globalRanking());
        assertEquals(Long.valueOf(742_918L), rank.regionRanking());
        assertNull(Rank.unranked().globalRanking());
        assertNull(Rank.unranked().regionRanking());
    }
}
