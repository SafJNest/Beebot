package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.R4B.api.dto.match.MatchResult;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.statistics.ProfileStatistics;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.api.dto.summoner.SummonerOverview;
import com.R4B.api.dto.summoner.SummonerView;
import com.R4B.lol.*;
import com.safjnest.utils.TimeConstant;

public class ProfileServiceTest {

    @Test
    public void exposesCanonicalSummonerViewData() {
        ProfileStatistics statistics = new ProfileStatistics(0);
        ProfileStatisticsAccumulator.of(statistics).add(match("top", LaneType.TOP), GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        ProfileStatisticsAccumulator.of(statistics).add(match("none", LaneType.NONE), GameQueueType.ARAM, LaneType.NONE);

        SummonerView page = SummonerProjection.view(
            new Summoner("puuid", "Name#TAG", com.R4B.lol.LeagueShard.EUW1, 10, 27),
            Map.of(GameQueueType.RANKED_FLEX_SR, new Rank(TierDivisionType.BRONZE_II, 15, 76, 131)),
            statistics,
            List.of(),
            java.util.Map.of(1, new SummonerOverview.Champion("Annie", "image"))
        );

        assertEquals(1, page.overview().statistics().laneStats.get(0).games);
        assertEquals(LaneType.TOP, page.overview().statistics().laneStats.get(0).reference);
        assertEquals(TierDivisionType.BRONZE_II, page.ranks().get(GameQueueType.RANKED_FLEX_SR).tier());
        assertEquals(27, page.summoner().icon());
        assertEquals("Annie", page.overview().champions().get(1).name());
    }

    @Test
    public void treatsOnlyRecentlySeenMonthOldAggregatesAsStale() {
        long now = 1_800_000_000_000L;

        assertTrue(ProfileService.isStale("puuid", 0, now, now));
        assertFalse(ProfileService.isStale("puuid", now - TimeConstant.DAY * 29, now, now));
        assertTrue(ProfileService.isStale("puuid", now - TimeConstant.DAY * 45, now, now));
        assertFalse(ProfileService.isStale("puuid", now - TimeConstant.DAY * 45,
            now - TimeConstant.DAY * 61, now));
    }

    private static MatchResult match(String id, LaneType lane) {
        return new MatchResult(
            id, GameQueueType.ARAM, 1_000, 2_000, true, "2/1/3", 1, lane, 100, 10, 100, 10, 10,
            List.of(), List.of(), List.of(Participant.forMatchResult(2, "puuid", "BLUE"))
        );
    }

}
