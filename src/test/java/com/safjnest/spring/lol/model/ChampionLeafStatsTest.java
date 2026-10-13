package com.safjnest.spring.lol.model;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.*;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.safjnest.spring.lol.model.statistics.shared.ChampionLeafStats;
import com.safjnest.spring.lol.model.statistics.shared.ChampionNode;
import com.safjnest.spring.lol.model.statistics.shared.TrendStats;

public class ChampionLeafStatsTest {

    @Test
    public void noLaneViewMergesPersistedLanesIncludingTrend() {
        ChampionLeafStats top = new ChampionLeafStats();
        top.games = 10;
        top.wins = 6;
        top.kills = 20;
        top.trend = new TrendStats(8, 5);
        ChampionLeafStats jungle = new ChampionLeafStats();
        jungle.games = 4;
        jungle.wins = 3;
        jungle.kills = 7;
        jungle.trend = new TrendStats(2, 1);
        ChampionNode node = new ChampionNode();
        node.lanes.put(LaneType.TOP.name(), top);
        node.lanes.put(LaneType.JUNGLE.name(), jungle);

        ChampionLeafStats merged = node.overall();

        assertEquals(14, merged.games);
        assertEquals(9, merged.wins);
        assertEquals(27, merged.kills);
        assertEquals(10, merged.trend.games);
        assertEquals(6, merged.trend.wins);
    }
}
