package com.safjnest.lol.model;
import com.safjnest.lol.service.FilterDefaults;
import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;

public class FilterTest {

    @Test
    public void summonerKeyRoundTripPreservesEveryFilterField() {
        Filter source = FilterDefaults.create()
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
        Filter filter = FilterDefaults.canonical();

        assertEquals(season == null ? 0 : season.start(), filter.timeStart());
        assertEquals(season == null ? 0 : season.end(), filter.timeEnd());
        assertEquals(0, filter.champion());
        assertEquals(null, filter.queue());
        assertEquals(null, filter.patch());
    }

    @Test
    public void championScopeIncludesRankBehaviorButNotPeriod() {
        Filter base = FilterDefaults.create().setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD)
            .setPatch("15.14").setPeriod(100, 200);
        Filter exact = FilterDefaults.create().setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD)
            .setRankBehavior(Filter.RankBehavior.EXACT).setPatch("15.14").setPeriod(100, 201);
        Filter later = FilterDefaults.create().setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD)
            .setPatch("15.14").setPeriod(300, 400);

        assertTrue(!ChampionStatsScope.from(base).toKey().equals(ChampionStatsScope.from(exact).toKey()));
        assertEquals(ChampionStatsScope.from(base).toKey(), ChampionStatsScope.from(later).toKey());
        assertEquals(ChampionStatsScope.from(exact), ChampionStatsScope.fromKey(ChampionStatsScope.from(exact).toKey()));
        assertTrue(!base.genericKey().equals(exact.genericKey()));
        assertTrue(!base.pageKey().equals(exact.pageKey()));
    }

    @Test
    public void buildKeyIncludesRankBehavior() {
        Filter greaterOrEqual = FilterDefaults.create().setChampion(412).setLane(com.R4B.lol.LaneType.UTILITY)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD).setPatch("15.14");
        Filter exact = FilterDefaults.create().setChampion(412).setLane(com.R4B.lol.LaneType.UTILITY)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO).setRank(TierType.EMERALD)
            .setRankBehavior(Filter.RankBehavior.EXACT).setPatch("15.14");

        assertTrue(!greaterOrEqual.toKey().equals(exact.toKey()));
        assertEquals(Filter.RankBehavior.EXACT, FilterDefaults.fromKey(exact.toKey()).rankBehavior());
    }
}
