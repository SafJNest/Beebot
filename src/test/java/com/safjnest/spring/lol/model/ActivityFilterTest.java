package com.safjnest.spring.lol.model;
import com.safjnest.spring.lol.service.FilterDefaults;
import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.*;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActivityFilterTest {

    @Test
    public void defaultsToAllQueuesAndRolesWithFiveMinimumGames() {
        ActivityFilter filter = FilterDefaults.activity();

        assertEquals(5, filter.minGames());
        assertEquals(null, filter.queue());
        assertEquals(null, filter.lane());
        assertEquals(null, filter.patch());
        assertEquals(null, filter.rank());
    }

    @Test
    public void minimumGamesDoesNotChangeAggregationIdentity() {
        ActivityFilter first = FilterDefaults.activity().setMinGames(5);
        ActivityFilter second = FilterDefaults.activity().setMinGames(20);

        assertEquals(first.toSummonerKey(), second.toSummonerKey());
    }

    @Test
    public void aggregationFilterKeepsGlobalFilterFields() {
        ActivityFilter source = FilterDefaults.activity();
        source.setQueue(com.R4B.lol.GameQueueType.RANKED_FLEX_SR);
        source.setLane(com.R4B.lol.LaneType.TOP);
        source.setPatch("14.10");
        source.setMinGames(8);

        Filter filter = source.aggregationFilter();

        assertEquals(source.toSummonerKey(), filter.toSummonerKey());
        assertEquals(8, source.minGames());
    }
}
