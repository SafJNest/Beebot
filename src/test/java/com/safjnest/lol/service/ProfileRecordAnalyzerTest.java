package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.RecordMetric;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class ProfileRecordAnalyzerTest {

    @Test
    public void keepsTheBestParticipantRecordAndOmitsIndividualGameShared() {
        ProfileRecordAnalyzer.Accumulator accumulator = ProfileRecordAnalyzer.accumulator("puuid", Filter.canonical());
        accumulator.accept(match("one", 1_000, 2_000, 20));
        accumulator.accept(match("two", 2_000, 5_500, 31));

        ProfileRecord kills = record(accumulator.finish(), RecordMetric.KILLS);
        ProfileRecord longest = record(accumulator.finish(), RecordMetric.LONGEST_GAME);

        assertEquals(31, kills.value);
        assertEquals("two", kills.matchId);
        assertNull(kills.gameShared);
        assertEquals(3_500, longest.value);
        assertTrue(longest.gameShared);
        assertEquals(Integer.valueOf(2261), kills.mmr);
    }

    @Test
    public void derivesSharedTeamAndIndividualTimelineRecords() {
        Match match = match("events", 1_000, 3_000, 5);
        match.eventData = Map.of(
            "champion_kills", List.of(Map.of("timestamp", 320L, "killer", 1, "kill_type", "first_blood")),
            "monster_events", List.of(
                Map.of("timestamp", 700L, "monster", "DRAGON", "subtype", "INFERNAL_DRAGON", "killer", 1, "killer_team", "BLUE"),
                Map.of("timestamp", 1_200L, "monster", "BARON_NASHOR", "subtype", "", "killer", 1, "killer_team", "BLUE"),
                Map.of("timestamp", 1_800L, "monster", "DRAGON", "subtype", "ELDER_DRAGON", "killer", 1, "killer_team", "BLUE")
            )
        );

        ProfileRecordAnalyzer.Accumulator accumulator = ProfileRecordAnalyzer.accumulator("puuid", Filter.canonical());
        accumulator.accept(match);
        List<ProfileRecord> records = accumulator.finish();

        assertEquals(320, record(records, RecordMetric.FIRST_BLOOD_TIME).value);
        assertEquals(1, record(records, RecordMetric.BARON_KILLS).value);
        assertEquals(700, record(records, RecordMetric.FIRST_DRAKE_TIME).value);
        assertEquals(1, record(records, RecordMetric.ELDERS_TAKEN).value);
        assertTrue(record(records, RecordMetric.FIRST_BARON_TIME).gameShared);
        assertEquals("puuid", record(records, RecordMetric.FIRST_BARON_TIME).actorPuuid);
    }

    @Test
    public void resolvesHistoricalTimelineParticipantIds() {
        Match match = match("legacy-events", 1_000, 3_000, 5);
        match.participants.get(0).id = 0;
        match.eventData = Map.of(
            "participants", Map.of("1", "puuid"),
            "champion_kills", List.of(Map.of("timestamp", 320L, "killer", 1, "kill_type", "first_blood"))
        );

        ProfileRecordAnalyzer.Accumulator accumulator = ProfileRecordAnalyzer.accumulator("puuid", Filter.canonical());
        accumulator.accept(match);

        assertEquals(320, record(accumulator.finish(), RecordMetric.FIRST_BLOOD_TIME).value);
    }

    private static ProfileRecord record(List<ProfileRecord> records, RecordMetric metric) {
        for (ProfileRecord record : records) if (record.metric == metric) return record;
        throw new AssertionError("Missing record metric=" + metric);
    }

    private static Match match(String gameId, long start, long end, int kills) {
        Match match = new Match();
        match.gameId = gameId;
        match.leagueShard = LeagueShard.EUW1;
        match.timeStart = start;
        match.timeEnd = end;
        Participant player = new Participant();
        player.id = 1;
        player.puuid = "puuid";
        player.champion = 157;
        player.team = TeamType.BLUE;
        player.kills = kills;
        player.deaths = 2;
        player.assists = 10;
        player.cs = 200;
        player.damage = 30_000;
        player.damageTaken = 10_000;
        player.pentas = 1;
        player.rankProgress = new RankProgress(TierDivisionType.EMERALD_III, 161, 0, null, null);
        match.participants = List.of(player);
        return match;
    }
}
