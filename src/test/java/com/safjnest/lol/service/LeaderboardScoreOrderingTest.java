package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class LeaderboardScoreOrderingTest {

    @Test
    public void equalMmrMembersSortByScoreThenStableCompetitiveId() {
        byte[] lower = LeaderboardService.member("lower", GameQueueType.RANKED_SOLO_5X5, 2);
        byte[] higher = LeaderboardService.member("higher", GameQueueType.RANKED_SOLO_5X5, 400);

        assertTrue(Arrays.compareUnsigned(lower, higher) < 0);
        assertTrue(Arrays.compareUnsigned(
            LeaderboardService.member("a", GameQueueType.RANKED_SOLO_5X5, 10),
            LeaderboardService.member("b", GameQueueType.RANKED_SOLO_5X5, 10)) == Arrays.compareUnsigned(
                MongoDB.competitiveId("a", GameQueueType.RANKED_SOLO_5X5.name()).getData(),
                MongoDB.competitiveId("b", GameQueueType.RANKED_SOLO_5X5.name()).getData()));
        assertEquals(24, higher.length);
        assertTrue(Arrays.equals(
            MongoDB.competitiveId("higher", GameQueueType.RANKED_SOLO_5X5.name()).getData(),
            Arrays.copyOfRange(higher, Long.BYTES, higher.length)));
    }

    @Test
    public void changedScoreProducesAReplacementMemberForTheSameCompetitiveEntry() {
        byte[] previous = LeaderboardService.member("puuid", GameQueueType.RANKED_SOLO_5X5, 2);
        byte[] updated = LeaderboardService.member("puuid", GameQueueType.RANKED_SOLO_5X5, 3);

        assertTrue(!Arrays.equals(previous, updated));
    }
}
