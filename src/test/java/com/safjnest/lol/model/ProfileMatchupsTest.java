package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class ProfileMatchupsTest {

    @Test
    public void aggregatesOneChampionAcrossMultipleRoles() {
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("top", 100, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("mid", 200, 1, LaneType.MID, 2, LaneType.MID, false)
        ), "puuid", filter());

        assertEquals(1, matchups.champions().size());
        assertEquals(1, matchups.champions().get(0).champion());
        assertEquals(2, matchups.champions().get(0).stats().games);
        assertEquals(1, matchups.champions().get(0).matchups().size());
        assertEquals(2, matchups.champions().get(0).matchups().get(0).champion());
        assertEquals(2, matchups.champions().get(0).matchups().get(0).stats().games);
        assertEquals(1, matchups.champions().get(0).matchups().get(0).stats().wins);
    }

    @Test
    public void filtersByRoleAndIgnoresOpponentsOnAnotherLane() {
        Filter filter = filter();
        filter.setLane(LaneType.TOP);
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("top", 100, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("different-lane", 200, 1, LaneType.TOP, 3, LaneType.MID, true),
            match("mid", 300, 1, LaneType.MID, 2, LaneType.MID, true)
        ), "puuid", filter);

        assertEquals(2, matchups.champions().get(0).stats().games);
        assertEquals(1, matchups.champions().get(0).matchups().size());
        assertEquals(2, matchups.champions().get(0).matchups().get(0).champion());
    }

    @Test
    public void filtersByQueuePatchAndRole() {
        Filter filter = filter()
            .setQueue(GameQueueType.RANKED_FLEX_SR)
            .setPatch("14.10")
            .setLane(LaneType.TOP);
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("valid", 100, 1, LaneType.TOP, 2, LaneType.TOP, true, GameQueueType.RANKED_FLEX_SR, "14.10"),
            match("queue", 200, 1, LaneType.TOP, 3, LaneType.TOP, true, GameQueueType.TEAM_BUILDER_RANKED_SOLO, "14.10"),
            match("patch", 300, 1, LaneType.TOP, 4, LaneType.TOP, true, GameQueueType.RANKED_FLEX_SR, "14.9"),
            match("role", 400, 1, LaneType.MID, 5, LaneType.MID, true, GameQueueType.RANKED_FLEX_SR, "14.10")
        ), "puuid", filter);

        assertEquals(1, matchups.champions().get(0).stats().games);
        assertEquals(1, matchups.champions().get(0).matchups().size());
        assertEquals(2, matchups.champions().get(0).matchups().get(0).champion());
    }

    @Test
    public void minimumGamesFiltersOnlyMatchups() {
        ProfileMatchups matchups = ProfileMatchups.from(List.of(
            match("one", 100, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("two", 200, 1, LaneType.TOP, 2, LaneType.TOP, true),
            match("three", 300, 1, LaneType.TOP, 3, LaneType.TOP, false)
        ), "puuid", filter());

        ProfileMatchups filtered = matchups.withMinGames(2);

        assertEquals(1, filtered.champions().size());
        assertEquals(3, filtered.champions().get(0).stats().games);
        assertEquals(1, filtered.champions().get(0).matchups().size());
        assertEquals(2, filtered.champions().get(0).matchups().get(0).champion());
    }

    @Test
    public void roundTripsThroughSharedJsonCodec() {
        ProfileMatchups source = ProfileMatchups.from(
            List.of(match("one", 100, 1, LaneType.TOP, 2, LaneType.TOP, true)),
            "puuid", filter());

        ProfileMatchups decoded = JsonCodec.fromJson(JsonCodec.toJson(source), ProfileMatchups.class);
        ProfileMatchups bsonDecoded = JsonCodec.fromDocument(JsonCodec.toDocument(source), ProfileMatchups.class);

        assertTrue(decoded != null);
        assertEquals(1, decoded.champions().get(0).matchups().get(0).stats().games);
        assertTrue(bsonDecoded != null);
        assertEquals(1, bsonDecoded.champions().get(0).matchups().get(0).stats().games);
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

    private static Match match(
        String id,
        long time,
        int champion,
        LaneType playerLane,
        int opponentChampion,
        LaneType opponentLane,
        boolean win
    ) {
        return match(id, time, champion, playerLane, opponentChampion, opponentLane, win,
            GameQueueType.TEAM_BUILDER_RANKED_SOLO, "14.10");
    }

    private static Match match(
        String id,
        long time,
        int champion,
        LaneType playerLane,
        int opponentChampion,
        LaneType opponentLane,
        boolean win,
        GameQueueType queue,
        String patch
    ) {
        Match match = new Match();
        match.gameId = id;
        match.queue = queue;
        match.patch = patch;
        match.timeStart = time;
        match.timeEnd = time + 1;
        Participant player = participant("puuid", champion, playerLane, TeamType.BLUE, win);
        Participant opponent = participant("opponent-" + id, opponentChampion, opponentLane, TeamType.RED, !win);
        match.participants = List.of(player, opponent);
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
        return participant;
    }
}
