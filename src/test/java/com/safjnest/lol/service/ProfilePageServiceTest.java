package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ProfileMatch;
import com.safjnest.lol.model.ProfileMatchParticipant;
import com.safjnest.lol.model.ProfilePageData;
import com.safjnest.lol.model.ProfileStatistics;
import com.safjnest.lol.model.SummonerProfile;
import com.safjnest.lol.model.SummonerRank;
import com.safjnest.spring.dto.LolProfileView;
import com.safjnest.spring.util.LolApiMapper;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class ProfilePageServiceTest {

    @Test
    public void mapsAllPlayableRolesAndRankQueuesFromTheDomainPage() {
        ProfileStatistics statistics = new ProfileStatistics(0);
        statistics.add(match("top", LaneType.TOP), GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        statistics.add(match("none", LaneType.NONE), GameQueueType.ARAM, LaneType.NONE);

        ProfilePageData page = new ProfilePageData(
            new SummonerProfile(1, "puuid", "Name#TAG", "EUW1", 10, 27),
            List.of(new SummonerRank(GameQueueType.RANKED_FLEX_SR, TierDivisionType.BRONZE_II, 15, 76, 131)),
            statistics,
            List.of(),
            java.util.Map.of(1, new com.safjnest.lol.model.ProfileChampion("Annie", "image"))
        );

        LolProfileView view = LolApiMapper.toProfileView(page);

        assertEquals(5, view.roles().size());
        assertEquals(1, view.roles().stream().filter(role -> role.role().equals("TOP")).findFirst().orElseThrow().games());
        assertEquals(0, view.roles().stream().filter(role -> role.role().equals("SUPPORT")).findFirst().orElseThrow().games());
        assertEquals(GameQueueType.RANKED_FLEX_SR.name(), view.profile().rank().get(0).queue());
        assertEquals(LeagueHandler.getSummonerProfilePic(27), view.profile().iconUrl());
        assertEquals("BLUE", view.recentMatches().get(0).participants().get(0).team());
    }

    private static ProfileMatch match(String id, LaneType lane) {
        return new ProfileMatch(
            id, GameQueueType.ARAM, 1_000, 2_000, true, "2/1/3", 1, lane, 100, 10, 100, 10, 10,
            List.of(), List.of(), List.of(new ProfileMatchParticipant(2, "puuid", "BLUE"))
        );
    }
}
