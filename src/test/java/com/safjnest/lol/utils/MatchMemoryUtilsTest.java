package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.safjnest.lol.champion.ChampionStatsData;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

public class MatchMemoryUtilsTest {

    @Test
    public void releasesMatchEventTreesAndNestedCollections() {
        Map<String, Object> nestedEvent = new LinkedHashMap<>();
        List<Object> championKills = new ArrayList<>();
        championKills.add(nestedEvent);
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("champion_kills", championKills);

        JSONArray frames = new JSONArray();
        frames.put(new JSONObject().put("events", new JSONArray().put(new JSONObject().put("killer", 1))));
        JSONObject events = new JSONObject().put("frames", frames);

        Map<Object, List<Integer>> bans = new LinkedHashMap<>();
        List<Integer> blueBans = new ArrayList<>(List.of(1, 2, 3));
        bans.put("BLUE", blueBans);
        List<Participant> participants = new ArrayList<>(List.of(new Participant()));

        Match match = new Match();
        match.eventData = eventData;
        match.events = events;
        match.bans = (Map) bans;
        match.participants = participants;

        MatchMemoryUtils.release(match);

        assertNull(match.eventData);
        assertNull(match.events);
        assertNull(match.bans);
        assertNull(match.participants);
        assertEquals(0, eventData.size());
        assertEquals(0, championKills.size());
        assertEquals(0, nestedEvent.size());
        assertEquals(0, events.length());
        assertEquals(0, frames.length());
        assertEquals(0, bans.size());
        assertEquals(0, blueBans.size());
        assertEquals(0, participants.size());
    }

    @Test
    public void releasesRawChampionMatchEventsAndParticipants() {
        Map<String, Object> events = new LinkedHashMap<>();
        List<Object> monsterEvents = new ArrayList<>(List.of(new LinkedHashMap<>()));
        events.put("monster_events", monsterEvents);
        Map<String, Object> bans = new LinkedHashMap<>();
        bans.put("BLUE", new ArrayList<>(List.of(1, 2, 3)));
        List<ChampionStatsData.RawParticipant> participants = new ArrayList<>();
        participants.add(new ChampionStatsData.RawParticipant(1, null, true, null, "match", "1/0/1", 10, 100, "puuid"));

        ChampionStatsData.RawMatch match = new ChampionStatsData.RawMatch("match",
            new ChampionStatsData.MatchMeta(bans, events, 0, 0, null, null), participants);

        MatchMemoryUtils.release(match);

        assertEquals(0, events.size());
        assertEquals(0, monsterEvents.size());
        assertEquals(0, bans.size());
        assertEquals(0, participants.size());
    }
}
