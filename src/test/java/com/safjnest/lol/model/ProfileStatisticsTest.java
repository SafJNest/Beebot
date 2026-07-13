package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.safjnest.utils.KryoUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class ProfileStatisticsTest {

    @Test
    public void persistsTotalsAndEnumReferencesThroughKryo() {
        ProfileStatistics source = new ProfileStatistics(100);
        source.add(match("one", 1, "2/1/3", 10), GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        source.add(match("two", 2, "1/2/4", 20), GameQueueType.ARAM, LaneType.NONE);

        ProfileStatistics decoded = KryoUtils.decode(KryoUtils.encode(source), ProfileStatistics.class);

        assertEquals(2, decoded.total.games);
        assertEquals(3.33, decoded.total.kda, 0.001);
        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, decoded.queueStats.get(0).reference);
        assertEquals(1, decoded.laneStats.size());
        assertEquals(LaneType.TOP, decoded.laneStats.get(0).reference);
        assertEquals(2, decoded.recentMatches.size());
        assertEquals(1, decoded.recentMatches.get(0).participants().size());
    }

    @Test
    public void keepsOnlyFiveMostRecentMatches() {
        ProfileStatistics source = new ProfileStatistics(0);
        for (int i = 0; i < 6; i++) {
            source.add(match("game" + i, i, "1/0/0", 10), GameQueueType.ARAM, LaneType.NONE);
        }

        assertEquals(5, source.recentMatches.size());
        assertTrue(source.recentMatches.get(0).timeStart() > source.recentMatches.get(4).timeStart());
    }

    private static ProfileMatch match(String id, long time, String kda, int teamKills) {
        return new ProfileMatch(id, GameQueueType.ARAM, time, time + 1, true, kda, 1, LaneType.TOP,
            100, 10, 100, 10, teamKills, List.of(), List.of(), List.of(new ProfileMatchParticipant(2, "puuid", "BLUE")));
    }
}
