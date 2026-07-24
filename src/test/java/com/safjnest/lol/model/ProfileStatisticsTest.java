package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ProfileStatisticsTest {

    @Test
    public void persistsTotalsAndEnumReferencesThroughJson() {
        ProfileStatistics source = new ProfileStatistics(100);
        source.add(match("one", 1, "2/1/3", 10), GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        source.add(match("two", 2, "1/2/4", 20), GameQueueType.ARAM, LaneType.NONE);

        String json = JsonCodec.toJson(source);
        ProfileStatistics decoded = JsonCodec.fromJson(json, ProfileStatistics.class);

        assertFalse(json.contains("legacyPayload"));
        assertEquals(2, decoded.total.games);
        assertEquals(3.33, decoded.total.kda, 0.001);
        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, decoded.queueStats.get(0).reference);
        assertEquals(1, decoded.laneStats.size());
        assertEquals(LaneType.TOP, decoded.laneStats.get(0).reference);
        assertFalse(json.contains("recentMatches"));
    }

    @Test
    public void persistsMatchResultAndParticipantThroughJson() {
        MatchResult source = match("game", 1, "2/1/3", 10);

        MatchResult decoded = JsonCodec.fromJson(JsonCodec.toJson(source), MatchResult.class);

        assertEquals(source.gameId(), decoded.gameId());
        assertEquals(source.participants().get(0).puuid(), decoded.participants().get(0).puuid());
        assertNull(JsonCodec.fromJson("not-json", ProfileStatistics.class));
    }

    @Test
    public void aggregatesMatchOnlyWhenCompleteFilterMatches() {
        Match match = new Match();
        match.leagueShard = LeagueShard.EUW1;
        match.queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        match.rank = TierType.GOLD;
        match.patch = "14.10";
        match.timeStart = 100;
        match.timeEnd = 200;

        Participant player = new Participant();
        player.puuid = "puuid";
        player.champion = 1;
        player.lane = LaneType.TOP;
        player.team = TeamType.BLUE;
        player.win = true;
        player.kda = "3/1/4";
        player.damage = 1000;
        player.gain = 21;
        player.pings.put("danger", 2);

        Participant opponent = new Participant();
        opponent.puuid = "opponent";
        opponent.champion = 2;
        opponent.lane = LaneType.TOP;
        opponent.team = TeamType.RED;
        opponent.kda = "1/3/1";
        match.participants = List.of(player, opponent);

        Filter filter = new Filter()
            .setChampion(1)
            .setLane(LaneType.TOP)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setRank(TierType.GOLD)
            .setPatch("14.10")
            .setRegion(LeagueShard.EUW1)
            .setOpponent(2)
            .setPeriod(100, 200);
        ProfileStatistics statistics = new ProfileStatistics(filter.timeStart());
        statistics.add(match, "puuid", filter);

        assertEquals(1, statistics.total.games);
        assertEquals(21, statistics.total.lpGain);
        assertEquals(Long.valueOf(2), statistics.pings.get("danger"));
        assertEquals(1, statistics.matchups.get(2).games);
    }

    private static MatchResult match(String id, long time, String kda, int teamKills) {
        return new MatchResult(id, GameQueueType.ARAM, time, time + 1, true, kda, 1, LaneType.TOP,
            100, 10, 100, 10, teamKills, List.of(), List.of(), List.of(Participant.forMatchResult(2, "puuid", "BLUE")));
    }
}
