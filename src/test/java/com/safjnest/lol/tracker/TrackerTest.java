package com.safjnest.lol.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.PatchUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class TrackerTest {

    @Test
    public void missingPreviousMatchKeepsGainAtZero() {
        assertEquals(0, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.GOLD_II,
                70,
                null));
    }

    @Test
    public void sameRankUsesLpDelta() {
        assertEquals(20, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.GOLD_II,
                70,
                previous(TierDivisionType.GOLD_II, 50)));
    }

    @Test
    public void divisionChangeUsesBoundaryCalculation() {
        assertEquals(-30, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.PLATINUM_I,
                0,
                previous(TierDivisionType.EMERALD_IV, 70)));
    }

    @Test
    public void diamondPromotionToMasterUsesPromotionCalculation() {
        assertEquals(20, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.MASTER_I,
                80,
                previous(TierDivisionType.DIAMOND_I, 0)));
    }

    @Test
    public void masterPlusUsesLpDeltaAcrossTierChanges() {
        assertEquals(10, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.MASTER_I,
                110,
                previous(TierDivisionType.MASTER_I, 100)));
        assertEquals(15, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.GRANDMASTER_I,
                115,
                previous(TierDivisionType.MASTER_I, 100)));
        assertEquals(-10, Tracker.calculateGain(
                GameQueueType.TEAM_BUILDER_RANKED_SOLO,
                TierDivisionType.CHALLENGER_I,
                90,
                previous(TierDivisionType.GRANDMASTER_I, 100)));
    }

    @Test
    public void previousMatchRankTakesPrecedenceOverStoredRank() {
        Participant previous = previous(TierDivisionType.GOLD_II, 70);
        Rank stored = new Rank(GameQueueType.RANKED_SOLO_5X5, TierDivisionType.GOLD_I, 10, 0, 0);

        assertEquals(previous, Tracker.rankBaseline(previous, stored));
    }

    @Test
    public void storedRankIsTheFallbackBaseline() {
        Rank stored = new Rank(GameQueueType.RANKED_SOLO_5X5, TierDivisionType.GOLD_II, 50, 0, 0);

        Participant baseline = Tracker.rankBaseline(null, stored);
        assertEquals(TierDivisionType.GOLD_II, baseline.rank);
        assertEquals(50, baseline.lp);
        assertEquals(0, Tracker.calculateGain(GameQueueType.TEAM_BUILDER_RANKED_SOLO, TierDivisionType.GOLD_II, 50, baseline));
    }

    @Test
    public void missingRankReferenceKeepsGainAtZero() {
        assertEquals(null, Tracker.rankBaseline(null, null));
        assertEquals(0, Tracker.calculateGain(GameQueueType.TEAM_BUILDER_RANKED_SOLO, TierDivisionType.GOLD_II, 50, null));
    }

    @Test
    public void championStatsShareGlobalKeyWhileBuildsRemainChampionSpecific() {
        Filter first = championFilter(1);
        Filter second = championFilter(2);

        assertEquals(first.genericKey(), second.genericKey());
        assertNotEquals(first.toKey(), second.toKey());
    }

    private static Filter championFilter(int champion) {
        List<String> patches = PatchUtils.getPatches();
        if (patches.isEmpty()) patches.add("0.0");
        return new Filter().setChampion(champion);
    }

    private static Participant previous(TierDivisionType rank, int lp) {
        Participant participant = new Participant();
        participant.rank = rank;
        participant.lp = lp;
        return participant;
    }
}
