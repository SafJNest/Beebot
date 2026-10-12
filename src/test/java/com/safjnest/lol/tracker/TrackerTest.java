package com.safjnest.lol.tracker;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.R4B.api.dto.match.RankProgress;
import com.R4B.lol.*;
import com.safjnest.lol.utils.RankProgressUtils;

public class TrackerTest {

    @Test
    public void missingPreviousMatchKeepsGainAtZero() {
        assertEquals(0, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.GOLD_II, 70),
                null));
    }

    @Test
    public void sameRankUsesLpDelta() {
        assertEquals(20, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.GOLD_II, 70),
                progress(TierDivisionType.GOLD_II, 50)));
    }

    @Test
    public void divisionChangeUsesBoundaryCalculation() {
        assertEquals(-30, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.PLATINUM_I, 0),
                progress(TierDivisionType.EMERALD_IV, 70)));
    }

    @Test
    public void diamondPromotionToMasterUsesPromotionCalculation() {
        assertEquals(20, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.MASTER_I, 80),
                progress(TierDivisionType.DIAMOND_I, 0)));
    }

    @Test
    public void masterPlusUsesLpDeltaAcrossTierChanges() {
        assertEquals(10, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.MASTER_I, 110),
                progress(TierDivisionType.MASTER_I, 100)));
        assertEquals(15, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.GRANDMASTER_I, 115),
                progress(TierDivisionType.MASTER_I, 100)));
        assertEquals(-10, RankProgressUtils.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.CHALLENGER_I, 90),
                progress(TierDivisionType.GRANDMASTER_I, 100)));
    }

    @Test
    public void unrankedTransitionsHaveDefinedGain() {
        assertEquals(42, RankProgressUtils.calculateGain(GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.IRON_IV, 42), progress(TierDivisionType.UNRANKED, 0)));
        assertEquals(0, RankProgressUtils.calculateGain(GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                progress(TierDivisionType.UNRANKED, 0), progress(TierDivisionType.IRON_IV, 42)));
    }

    private static RankProgress progress(TierDivisionType rank, int lp) {
        return new RankProgress(rank, lp, null, null, null);
    }
}
