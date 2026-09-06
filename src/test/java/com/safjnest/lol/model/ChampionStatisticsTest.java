package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class ChampionStatisticsTest {

    private static final int THRESH_CHAMPION_ID = 412;

    @Test
    public void persistsMatchupsWithJson() {
        ChampionStatistics source = new ChampionStatistics(
            null,
            new ChampionStatistics.Overview(100, 20, 5, 11, 0.55, 0.2, 0.05, null, null, null, null),
            List.of(new ChampionStatistics.LaneStat(LaneType.UTILITY, 20, 0.55)),
            Map.of(THRESH_CHAMPION_ID, new ChampionStatistics.Matchup(20, 0.55)),
            List.of(),
            List.of(),
            null
        );

        String json = source.toJson();
        ChampionStatistics decoded = ChampionStatistics.fromJson(json);

        assertTrue(json.contains("\"412\""));
        assertEquals(source, decoded);
        assertNull(ChampionStatistics.fromJson("not-json"));
    }
}
