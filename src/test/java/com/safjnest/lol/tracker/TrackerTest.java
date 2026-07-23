package com.safjnest.lol.tracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.safjnest.lol.model.match.Participant;

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

    private static Participant previous(TierDivisionType rank, int lp) {
        Participant participant = new Participant();
        participant.rank = rank;
        participant.lp = lp;
        return participant;
    }
}
