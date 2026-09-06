package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;

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

    @Test
    public void canonicalFilterUsesTheCurrentSeason() {
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        Filter filter = Filter.canonical();

        assertEquals(season == null ? 0 : season.start(), filter.timeStart());
        assertEquals(season == null ? 0 : season.end(), filter.timeEnd());
        assertEquals(0, filter.champion());
        assertEquals(null, filter.queue());
        assertEquals(null, filter.patch());
    }

    @Test
    public void championScopeAndCacheKeysIncludeRankBehaviorAndPeriod() {
        Filter base = new Filter().setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD)
            .setPatch("15.14").setPeriod(100, 200);
        Filter exact = new Filter().setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD)
            .setRankBehavior(Filter.RankBehavior.EXACT).setPatch("15.14").setPeriod(100, 201);

        assertTrue(!ChampionStatsScope.from(base).toKey().equals(ChampionStatsScope.from(exact).toKey()));
        assertTrue(!base.genericKey().equals(exact.genericKey()));
        assertTrue(!base.pageKey().equals(exact.pageKey()));
    }
}
