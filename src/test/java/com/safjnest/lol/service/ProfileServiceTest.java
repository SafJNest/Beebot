package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerOverview;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.utils.TimeConstant;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class ProfileServiceTest {

    @Test
    public void exposesCanonicalSummonerViewData() {
        ProfileStatistics statistics = new ProfileStatistics(0);
        statistics.add(match("top", LaneType.TOP), GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        statistics.add(match("none", LaneType.NONE), GameQueueType.ARAM, LaneType.NONE);

        SummonerView page = SummonerView.from(
            new Summoner("puuid", "Name#TAG", no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1, 10, 27),
            List.of(new Rank(GameQueueType.RANKED_FLEX_SR, TierDivisionType.BRONZE_II, 15, 76, 131)),
            statistics,
            List.of(),
            java.util.Map.of(1, new SummonerOverview.Champion("Annie", "image")),
            List.of(match("recent", LaneType.TOP))
        );

        assertEquals(1, page.overview().statistics().laneStats.get(0).games);
        assertEquals(LaneType.TOP, page.overview().statistics().laneStats.get(0).reference);
        assertEquals(GameQueueType.RANKED_FLEX_SR, page.ranks().get(0).queue());
        assertEquals(27, page.summoner().icon());
        assertEquals("BLUE", page.overview().recentMatches().get(0).participants().get(0).team());
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
