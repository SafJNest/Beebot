package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ProfileStatisticsTest {

    @Test
    public void persistsTotalsAndEnumReferencesThroughJson() throws Exception {
        ProfileStatistics source = new ProfileStatistics(100);
        source.add(match("one", 1, "2/1/3", 10), GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        source.add(match("two", 2, "1/2/4", 20), GameQueueType.ARAM, LaneType.NONE);

        String json = JsonCodec.toJson(source);
        String apiJson = new ObjectMapper().writeValueAsString(source);
        ProfileStatistics decoded = JsonCodec.fromJson(json, ProfileStatistics.class);

        assertFalse(json.contains("legacyPayload"));
        assertEquals(2, decoded.total.games);
        assertEquals(3.33, decoded.total.kda, 0.001);
        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, decoded.queueStats.get(0).reference);
        assertEquals(1, decoded.laneStats.size());
        assertEquals(LaneType.TOP, decoded.laneStats.get(0).reference);
        assertTrue(decoded.total.context.isEmpty());
        assertTrue(decoded.queueStats.get(0).context.isEmpty());
        assertEquals(2, decoded.championStats.get(0).context.size());
        assertEquals(1, context(decoded.championStats.get(0), GameQueueType.RANKED_SOLO_5X5, "TOP").games);
        assertEquals(1, context(decoded.championStats.get(0), GameQueueType.ARAM, "UNKNOWN").games);
        assertTrue(apiJson.contains("\"context\""));
        assertFalse(apiJson.contains("\"context\":[]"));
        assertFalse(apiJson.contains("championContext"));
        assertFalse(json.contains("championContext"));
        assertFalse(json.contains("recentMatches"));
    }

    @Test
    public void exposesChampionStatsByCanonicalQueueAndLane() {
        ProfileStatistics statistics = new ProfileStatistics(0);
        statistics.add(match("solo-top", GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP, 1),
            GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP);
        statistics.add(match("solo-mid", GameQueueType.RANKED_SOLO_5X5, LaneType.MID, 2),
            GameQueueType.RANKED_SOLO_5X5, LaneType.MID);
        statistics.add(match("flex-top", GameQueueType.RANKED_FLEX_SR, LaneType.TOP, 3),
            GameQueueType.RANKED_FLEX_SR, LaneType.TOP);
        statistics.add(match("arena", GameQueueType.CHERRY, LaneType.NONE, 4),
            GameQueueType.CHERRY, LaneType.NONE);

        Stats<Integer> champion = statistics.championStats.get(0);

        assertEquals(4, champion.games);
        assertEquals(3, champion.context.size());
        assertEquals(1, context(champion, GameQueueType.RANKED_SOLO_5X5, "TOP").games);
        assertEquals(1, context(champion, GameQueueType.RANKED_SOLO_5X5, "MID").games);
        assertEquals(1, context(champion, GameQueueType.RANKED_FLEX_SR, "TOP").games);
        assertEquals(1, context(champion, GameQueueType.CHERRY, "UNKNOWN").games);
        assertEquals(1, cherryContext(champion).size());
    }

    @Test
    public void marksLegacyAggregatesWithoutChampionContextAsStale() {
        ProfileStatistics legacy = new ProfileStatistics(0);
        legacy.total.games = 1;
        legacy.championStats.add(new Stats<>(1));

        assertFalse(legacy.hasChampionContext());
        assertTrue(new ProfileStatistics().hasChampionContext());
    }

    @Test
    public void persistsMatchResultAndParticipantThroughJson() throws Exception {
        MatchResult source = match("game", 1, "2/1/3", 10);
        source.participants().get(0).rankProgress = new RankProgress(
                TierDivisionType.GOLD_II, 54, 0, TierDivisionType.GOLD_II, 54);

        String json = JsonCodec.toJson(source);
        String apiJson = new ObjectMapper().writeValueAsString(source);
        MatchResult decoded = JsonCodec.fromJson(json, MatchResult.class);

        assertEquals(source.gameId(), decoded.gameId());
        assertEquals(source.participants().get(0).puuid(), decoded.participants().get(0).puuid());
        assertEquals(TierDivisionType.GOLD_II, decoded.participants().get(0).rankProgress.rank);
        assertTrue(new ObjectMapper().readTree(apiJson).path("participants").get(0).has("rankProgress"));
        assertFalse(new ObjectMapper().readTree(apiJson).path("participants").get(0).has("rank"));
        assertFalse(new ObjectMapper().readTree(apiJson).path("participants").get(0).has("lp"));
        assertFalse(new ObjectMapper().readTree(apiJson).path("participants").get(0).has("gain"));
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
        player.rankProgress = new RankProgress(TierDivisionType.GOLD_II, 21, 21, null, null);
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
        assertEquals(1, context(statistics.championStats.get(0), GameQueueType.RANKED_SOLO_5X5, "TOP").games);
    }

    @Test
    public void addsChampionContextForCompleteMatches() {
        Match match = new Match();
        match.queue = GameQueueType.CHERRY;
        match.timeStart = 100;
        match.timeEnd = 200;

        Participant player = new Participant();
        player.puuid = "puuid";
        player.champion = 1;
        player.lane = LaneType.NONE;
        player.team = TeamType.BLUE;
        player.win = true;
        player.kda = "2/1/3";
        match.participants = List.of(player);

        ProfileStatistics statistics = new ProfileStatistics(0);
        statistics.add(match, "puuid", null);

        assertEquals(1, statistics.total.games);
        assertEquals(1, context(statistics.championStats.get(0), GameQueueType.CHERRY, "UNKNOWN").games);
        assertEquals(1, cherryContext(statistics.championStats.get(0)).size());
    }

    private static MatchResult match(String id, long time, String kda, int teamKills) {
        return match(id, GameQueueType.ARAM, LaneType.TOP, time, kda, teamKills);
    }

    private static MatchResult match(String id, GameQueueType queue, LaneType lane, long time) {
        return match(id, queue, lane, time, "2/1/3", 10);
    }

    private static MatchResult match(String id, GameQueueType queue, LaneType lane, long time, String kda, int teamKills) {
        return new MatchResult(id, queue, time, time + 1, true, kda, 1, lane,
            100, 10, 100, 10, teamKills, List.of(), List.of(), List.of(Participant.forMatchResult(2, "puuid", "BLUE")));
    }

    private static Stats<Void> context(Stats<Integer> champion, GameQueueType queue, String lane) {
        for (Map<GameQueueType, Map<String, Stats<Void>>> queueContext : champion.context) {
            Map<String, Stats<Void>> lanes = queueContext.get(queue);
            if (lanes != null && lanes.containsKey(lane)) return lanes.get(lane);
        }
        return null;
    }

    private static Map<String, Stats<Void>> cherryContext(Stats<Integer> champion) {
        for (Map<GameQueueType, Map<String, Stats<Void>>> queueContext : champion.context) {
            Map<String, Stats<Void>> lanes = queueContext.get(GameQueueType.CHERRY);
            if (lanes != null) return lanes;
        }
        return Map.of();
    }
}
