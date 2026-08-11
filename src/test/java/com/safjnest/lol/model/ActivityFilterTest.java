package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActivityFilterTest {

    @Test
    public void defaultsToAllQueuesAndRolesWithFiveMinimumGames() {
        ActivityFilter filter = new ActivityFilter();

        assertEquals(5, filter.minGames());
        assertEquals(null, filter.queue());
        assertEquals(null, filter.lane());
        assertEquals(null, filter.patch());
        assertEquals(null, filter.rank());
    }

    @Test
    public void minimumGamesDoesNotChangeAggregationIdentity() {
        ActivityFilter first = new ActivityFilter().setMinGames(5);
        ActivityFilter second = new ActivityFilter().setMinGames(20);

        assertEquals(first.toSummonerKey(), second.toSummonerKey());
    }

    @Test
    public void aggregationFilterKeepsGlobalFilterFields() {
        ActivityFilter source = new ActivityFilter();
        source.setQueue(no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType.RANKED_FLEX_SR);
        source.setLane(no.stelar7.api.r4j.basic.constants.types.lol.LaneType.TOP);
        source.setPatch("14.10");
        source.setMinGames(8);

        Filter filter = source.aggregationFilter();

        assertEquals(source.toSummonerKey(), filter.toSummonerKey());
        assertEquals(8, source.minGames());
    }
}
