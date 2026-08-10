package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class FilterTest {

    @Test
    public void summonerKeyRoundTripPreservesEveryFilterField() {
        Filter source = new Filter()
            .setChampion(99)
            .setLane(LaneType.MID)
            .setQueue(GameQueueType.RANKED_FLEX_SR)
            .setRank(TierType.DIAMOND)
            .setRankBehavior(Filter.RankBehavior.EXACT)
            .setPatch("15.16")
            .setRegion(LeagueShard.EUW1)
            .setOpponent(55)
            .setDuo(22)
            .setPeriod(1_700_000_000_000L, 1_701_000_000_000L);

        Filter restored = Filter.fromSummonerKey(source.toSummonerKey());

        assertEquals(source.toSummonerKey(), restored.toSummonerKey());
        assertEquals(Filter.RankBehavior.EXACT, restored.rankBehavior());
    }
}
