package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.ProfileMatchupLeaf;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;
import com.safjnest.lol.utils.MatchupTimelineUtils;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class ProfileMatchupsTest {

    @Test
    public void persistsMatchupsInsideTheirChampionQueuePositionLeaf() {
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("top", 100, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("mid", 200, 1, LaneType.MID, 2, LaneType.MID, false)
        ), "puuid", filter());

        ProfileMatchupLeaf top = matchups.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP");
        ProfileMatchupLeaf mid = matchups.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("MID");
        assertEquals(1, top.games);
        assertEquals(1, mid.games);
        assertEquals(Long.valueOf(18), top.championLevelTotal);
        assertEquals(1, top.matchups.get("2").games);
        assertEquals(Long.valueOf(18), top.matchups.get("2").championLevelTotal);
        assertEquals(1, mid.matchups.get("2").games);
    }

    @Test
    public void canonicalizesEquivalentRankedSoloQueues() {
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("current", 100, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("legacy", 200, 1, LaneType.TOP, 2, LaneType.TOP, false,
                GameQueueType.RANKED_SOLO_5X5, "14.10")
        ), "puuid", allQueuesFilter());

        assertEquals(2, matchups.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP").games);
    }

    @Test
    public void assignsMissingPositionToUnknownLeaf() {
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("arena", 100, 1, LaneType.NONE, 2, LaneType.NONE, true, GameQueueType.CHERRY, "14.10")
        ), "puuid", allQueuesFilter());

        assertEquals(1, matchups.champions().get(1).get(CanonicalQueue.ARENA).get("UNKNOWN").games);
    }

    @Test
    public void minimumGamesGroupsSmallLeafMatchupsUnderOthers() {
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("one", 100, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("two", 200, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("three", 300, 1, LaneType.TOP, 3, LaneType.TOP, false)
        ), "puuid", filter()).withMinGames(2);

        ProfileMatchupLeaf leaf = matchups.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP");
        assertEquals(3, leaf.games);
        assertEquals(2, leaf.matchups.size());
        assertTrue(leaf.matchups.containsKey("2"));
        assertEquals(2, leaf.matchups.get("2").games);
        assertEquals(1, leaf.matchups.get("others").games);
    }

    @Test
    public void serializesOnlyKeyedRawAccumulators() {
        ProfileMatchups matchups = ProfileMatchups.from(
            List.of(match("one", 100, 1, LaneType.TOP, 2, LaneType.TOP, true)), "puuid", filter());

        String json = JsonCodec.toJson(matchups);
        assertTrue(json.contains("\"RANKED_SOLO\""));
        assertTrue(json.contains("\"matchups\":{\"2\""));
        assertFalse(json.contains("\"reference\""));
        assertFalse(json.contains("\"avgKills\""));
        assertFalse(json.contains("\"winrate\""));
        assertFalse(json.contains("\"kda\""));
        assertFalse(json.contains("\"schemaVersion\""));
        assertTrue(json.contains("\"goldDiffAt15Sum\":0.0"));
        assertTrue(json.contains("\"goldDiffAt15Games\":0"));
        assertTrue(json.contains("\"csDiffAt15Sum\":0.0"));
        assertTrue(json.contains("\"csDiffAt15Games\":0"));
        assertTrue(json.contains("\"xpDiffAt15Sum\":0.0"));
        assertTrue(json.contains("\"xpDiffAt15Games\":0"));
        assertTrue(json.contains("\"killDiffAt15Sum\":0.0"));
        assertTrue(json.contains("\"killDiffAt15Games\":0"));
        assertTrue(json.contains("\"levelDiffAt15Sum\":0.0"));
        assertTrue(json.contains("\"levelDiffAt15Games\":0"));
        assertTrue(json.contains("\"plateDiffAt15Sum\":0.0"));
        assertTrue(json.contains("\"plateDiffAt15Games\":0"));
    }

    @Test
    public void aggregatesFifteenMinuteLaneAndDuoDifferencesWithCorrectSigns() {
        Match match = duoMatchWithTimeline();
        assertEquals(Integer.valueOf(3000), MatchupTimelineUtils.read(match).snapshots().get("puuid").gold());
        ProfileMatchups profile = ProfileMatchups.from(List.of(match), "puuid", filter());
        ProfileMatchupLeaf leaf = profile.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("BOT");

        ProfileLeafStats matchup = leaf.matchups.get("4");
        assertEquals(200d, matchup.goldDiffAt15Sum, 0d);
        assertEquals(10d, matchup.csDiffAt15Sum, 0d);
        assertEquals(200d, matchup.xpDiffAt15Sum, 0d);
        assertEquals(0d, matchup.killDiffAt15Sum, 0d);
        assertEquals(1d, matchup.levelDiffAt15Sum, 0d);
        assertEquals(1d, matchup.plateDiffAt15Sum, 0d);

        ProfileLeafStats synergy = leaf.synergies.get("2");
        assertEquals(500d, synergy.goldDiffAt15Sum, 0d);
        assertEquals(20d, synergy.csDiffAt15Sum, 0d);
        assertEquals(500d, synergy.xpDiffAt15Sum, 0d);
        assertEquals(1d, synergy.killDiffAt15Sum, 0d);
        assertEquals(1d, synergy.plateDiffAt15Sum, 0d);
    }

    @Test
    public void roundTripsThroughSharedJsonCodec() {
        ProfileMatchups source = ProfileMatchups.from(
            List.of(match("one", 100, 1, LaneType.TOP, 2, LaneType.TOP, true)), "puuid", filter());

        ProfileMatchups decoded = JsonCodec.fromJson(JsonCodec.toJson(source), ProfileMatchups.class);
        ProfileMatchups bsonDecoded = JsonCodec.fromDocument(JsonCodec.toDocument(source), ProfileMatchups.class);

        assertTrue(decoded.hasLeafMatchups());
        assertEquals(1, decoded.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP").matchups.get("2").games);
        assertTrue(bsonDecoded.hasLeafMatchups());
        assertEquals(1, bsonDecoded.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP").matchups.get("2").games);
    }

    private static Filter filter() {
        return new Filter()
            .setChampion(0)
            .setLane(null)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setRank(null)
            .setPatch(null)
            .setRegion(null)
            .setPeriod(0, 0);
    }

    private static Filter allQueuesFilter() {
        return filter().setQueue(null);
    }

    private static Match match(String id, long time, int champion, LaneType playerLane, int opponentChampion,
                               LaneType opponentLane, boolean win) {
        return match(id, time, champion, playerLane, opponentChampion, opponentLane, win,
            GameQueueType.TEAM_BUILDER_RANKED_SOLO, "14.10");
    }

    private static Match match(String id, long time, int champion, LaneType playerLane, int opponentChampion,
                               LaneType opponentLane, boolean win, GameQueueType queue, String patch) {
        Match match = new Match();
        match.gameId = id;
        match.queue = queue;
        match.patch = patch;
        match.timeStart = time;
        match.timeEnd = time + 1;
        match.participants = List.of(
            participant("puuid", champion, playerLane, TeamType.BLUE, win),
            participant("opponent-" + id, opponentChampion, opponentLane, TeamType.RED, !win)
        );
        return match;
    }

    private static Participant participant(String puuid, int champion, LaneType lane, TeamType team, boolean win) {
        Participant participant = new Participant();
        participant.puuid = puuid;
        participant.champion = champion;
        participant.lane = lane;
        participant.team = team;
        participant.win = win;
        participant.kda = "2/1/3";
        participant.damage = 100;
        participant.cs = 20;
        participant.goldEarned = 300;
        participant.championLevel = 18;
        return participant;
    }

    private static Match duoMatchWithTimeline() {
        Match match = new Match();
        match.gameId = "timeline-duo";
        match.queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        match.patch = "14.10";
        match.timeStart = 100;
        match.timeEnd = 1_800_100;
        match.participants = List.of(
            participant("puuid", 1, LaneType.BOT, TeamType.BLUE, true),
            participant("ally", 2, LaneType.UTILITY, TeamType.BLUE, true),
            participant("enemy", 4, LaneType.BOT, TeamType.RED, false),
            participant("enemy-ally", 5, LaneType.UTILITY, TeamType.RED, false)
        );
        match.events = new JSONObject("""
            {
              "participants":{"1":"puuid","2":"ally","3":"enemy","4":"enemy-ally"},
              "snapshots":[{"timestamp":900000,"minute":15,"participants":{
                "1":{"total_gold":3000,"cs":100,"xp":5000,"level":11},
                "2":{"total_gold":2000,"cs":50,"xp":3000,"level":9},
                "3":{"total_gold":2800,"cs":90,"xp":4800,"level":10},
                "4":{"total_gold":1700,"cs":40,"xp":2700,"level":9}
              }}],
              "champion_kills":[
                {"timestamp":900000,"killer":1},
                {"timestamp":900000,"killer":2},
                {"timestamp":900000,"killer":3}
              ],
              "turret_plate_events":[
                {"timestamp":900000,"team":"BLUE","lane":"BOT"},
                {"timestamp":900000,"team":"BLUE","lane":"BOT"},
                {"timestamp":900000,"team":"RED","lane":"BOT"}
              ]
            }
            """);
        return match;
    }
}
