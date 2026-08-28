package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class ProfileStatisticsTest {

    @Test
    public void persistsOnlyCanonicalLeavesWithoutDerivedFields() throws Exception {
        ProfileStatistics statistics = new ProfileStatistics();
        statistics.add(match(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP, 12, 900), "puuid", null);
        statistics.finish();

        String json = new ObjectMapper().writeValueAsString(statistics);
        assertTrue(json.contains("\"champions\""));
        assertTrue(json.contains("\"RANKED_SOLO\""));
        assertFalse(json.contains("\"total\""));
        assertFalse(json.contains("\"queueStats\""));
        assertFalse(json.contains("\"avgKills\""));
        assertFalse(json.contains("\"reference\""));
        assertFalse(json.contains("\"context\""));
        assertFalse(json.contains("\"schemaVersion\""));
        assertFalse(json.contains("\"matchups\""));
        assertFalse(json.contains("\"duoStats\""));
    }

    @Test
    public void aggregatesChampionLevelAndOmitsMissingDamageTaken() throws Exception {
        ProfileStatistics statistics = new ProfileStatistics();
        statistics.add(match(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LaneType.TOP, 18, null), "puuid", null);
        statistics.finish();

        Stats<Void> leaf = statistics.champions.get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP");
        assertEquals(Long.valueOf(18), leaf.championLevelTotal);
        assertNull(leaf.damageTaken);
        assertEquals(1, leaf.games);
        assertFalse(new ObjectMapper().writeValueAsString(statistics).contains("damageTaken"));
    }

    @Test
    public void assignsUnavailableLaneToUnknownLeaf() {
        ProfileStatistics statistics = new ProfileStatistics();
        statistics.add(match(GameQueueType.RANKED_SOLO_5X5, LaneType.NONE, 10, 0), "puuid", null);

        assertEquals(1, statistics.champions.get(1).get(CanonicalQueue.RANKED_SOLO).get("UNKNOWN").games);
    }

    @Test
    public void persistsArenaFieldsOnlyOnArenaLeaf() throws Exception {
        ProfileStatistics statistics = new ProfileStatistics();
        statistics.add(match(GameQueueType.RANKED_SOLO_5X5, LaneType.TOP, 18, 100), "puuid", null);
        statistics.add(match(GameQueueType.CHERRY, LaneType.NONE, 18, 100, 3), "puuid", null);
        statistics.finish();

        Stats<Void> ranked = statistics.champions.get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP");
        Stats<Void> arena = statistics.champions.get(1).get(CanonicalQueue.ARENA).get("UNKNOWN");
        assertEquals(Double.valueOf(3), arena.avgArenaPlacement());
        assertEquals(3, arena.arenaPlacementSum);
        assertFalse(new ObjectMapper().writeValueAsString(ranked).contains("arena"));
        assertFalse(new ObjectMapper().writeValueAsString(statistics).contains("arenaGames"));
    }

    private static Match match(GameQueueType queue, LaneType lane, int championLevel, Integer damageTaken) {
        return match(queue, lane, championLevel, damageTaken, 0);
    }

    private static Match match(GameQueueType queue, LaneType lane, int championLevel, Integer damageTaken, int placement) {
        Match match = new Match();
        match.leagueShard = LeagueShard.EUW1;
        match.queue = queue;
        match.timeStart = 1_000;
        match.timeEnd = 2_000;
        Participant participant = new Participant();
        participant.puuid = "puuid";
        participant.champion = 1;
        participant.lane = lane;
        participant.team = TeamType.BLUE;
        participant.win = true;
        participant.kda = "2/1/3";
        participant.championLevel = championLevel;
        participant.damageTaken = damageTaken;
        participant.subTeam = 1;
        participant.subTeamPlacement = placement;
        match.participants = List.of(participant);
        return match;
    }
}
