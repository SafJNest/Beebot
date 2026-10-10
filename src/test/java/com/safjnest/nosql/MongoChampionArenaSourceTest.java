package com.safjnest.nosql;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.utils.MatchMemoryUtils;
import com.safjnest.lol.utils.MatchupTimelineUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class MongoChampionArenaSourceTest {

    @Test
    public void leftJoinDeliversMatchesWithAndWithoutEventsAndAllParticipants() throws Exception {
        List<Document> sources = new ArrayList<>(List.of(match("EUW1_1"), match("EUW1_2")));
        Map<String, Document> events = new HashMap<>();
        events.put("EUW1_1", event("EUW1_1", timeline()));

        List<Match> matches = hydrate(sources, events);
        MatchMemoryUtils.release(sources);
        MatchMemoryUtils.release(events);

        assertEquals(2, matches.size());
        Match joined = matches.get(0);
        assertEquals("26.19.123", joined.patch);
        assertEquals(GameQueueType.CHERRY, joined.queue);
        assertEquals(2, joined.participants.size());
        assertEquals(1, joined.participants.get(0).id);
        assertEquals(2, joined.participants.get(1).id);
        assertEquals("other", joined.participants.get(1).puuid);
        assertEquals(2, joined.participants.get(0).subTeamPlacement);
        assertEquals(3006, joined.participants.get(0).boots);
        assertEquals(447001, joined.participants.get(0).item0);
        assertEquals(List.of(101, 0, 103), joined.participants.get(0).augments);
        assertTrue(MatchupTimelineUtils.hasTimeline(joined.events));
        assertEquals(447001, joined.events.getJSONArray("item_events").getJSONObject(0).getInt("item"));
        assertNull(joined.eventData);
        assertFalse(MatchupTimelineUtils.hasTimeline(matches.get(1).events));
        assertNull(matches.get(1).eventData);
        assertEquals(2, matches.get(1).participants.size());
        MatchMemoryUtils.release(matches);
    }

    @Test
    public void absentTimelineRemainsEligibleForArenaButNotStandard() throws Exception {
        Document source = match("EUW1_3");
        Filter filter = Filter.championBuild(1, "26.19.123", GameQueueType.CHERRY);
        assertTrue(MongoDB.championBuildRecords(source, filter).isEmpty());
        List<Match> result = hydrate(List.of(source), Map.of());
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).participants.get(0).champion);
        assertFalse(MatchupTimelineUtils.hasTimeline(result.get(0).events));
        MatchMemoryUtils.release(result);
    }

    @Test
    public void emptyEventObjectIsDeliveredWithoutUsableTimeline() throws Exception {
        List<Match> result = hydrate(List.of(match("EUW1_4")), Map.of("EUW1_4", event("EUW1_4", "{}")));
        assertEquals(1, result.size());
        assertFalse(MatchupTimelineUtils.hasTimeline(result.get(0).events));
        MatchMemoryUtils.release(result);
    }

    @Test
    public void corruptChecksumAndJsonAreErrorsInsteadOfMissingTimeline() throws Exception {
        Document checksum = event("EUW1_5", timeline());
        checksum.put("checksum", "corrupt");
        assertHydrationFails(checksum);
        assertHydrationFails(event("EUW1_5", "{broken"));
        Document wrongSize = event("EUW1_5", timeline());
        wrongSize.put("uncompressedBytes", 1);
        assertHydrationFails(wrongSize);
        Document encoding = event("EUW1_5", timeline());
        encoding.put("encoding", "unknown");
        assertHydrationFails(encoding);
    }

    @Test
    public void queryUsesFullPatchAlongsideIndexedPatchMajor() throws Exception {
        Filter filter = Filter.championBuild(1, "26.19.123", GameQueueType.CHERRY);
        Method method = MongoDB.class.getDeclaredMethod("championArenaMatchFilter", Filter.class);
        method.setAccessible(true);
        Bson query = (Bson) method.invoke(null, filter);
        String json = query.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson();
        assertTrue(json.contains("\"patch\": \"26.19.123\""));
        assertTrue(json.contains("\"patchMajor\": \"26.19\""));
        assertTrue(json.contains("\"queue\": \"CHERRY\""));
        assertTrue(json.contains("$elemMatch"));
        assertTrue(json.contains("\"champion\": 1"));
    }

    @Test
    public void invalidScopeAndOversizedBatchFailBeforeOpeningMongo() throws Exception {
        Filter standard = Filter.championBuild(1, "26.19.123", GameQueueType.ARAM);
        assertThrows(IllegalArgumentException.class,
            () -> MongoDB.forEachChampionArenaMatchBatch(standard, 100, batch -> {}));
        Filter arena = Filter.championBuild(1, "26.19.123", GameQueueType.CHERRY);
        assertThrows(IllegalArgumentException.class,
            () -> MongoDB.forEachChampionArenaMatchBatch(arena, 101, batch -> {}));
        assertThrows(IllegalArgumentException.class,
            () -> MongoDB.forEachChampionArenaMatchBatch(arena, 0, batch -> {}));
        assertThrows(IllegalArgumentException.class,
            () -> MongoDB.forEachChampionArenaMatchBatch(arena.setOpponent(2), 100, batch -> {}));
        assertThrows(IllegalArgumentException.class,
            () -> MongoDB.forEachChampionArenaMatchBatch(Filter.championBuild(1, "26.19", GameQueueType.CHERRY), 100, batch -> {}));
        assertThrows(IllegalArgumentException.class,
            () -> MongoDB.forEachChampionArenaMatchBatch(Filter.championBuild(1, "26.19.123", GameQueueType.CHERRY)
                .setRankBehavior(Filter.RankBehavior.EXACT), 100, batch -> {}));
    }

    @Test
    public void projectionContainsIdentityFallbackFactsAndNoArrayElemMatch() throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("championArenaProjection");
        method.setAccessible(true);
        Document projection = (Document) method.invoke(null);
        for (String field : List.of("_id", "region", "queue", "patch", "participants.id", "participants.puuid",
                "participants.champion", "participants.win", "participants.subTeamPlacement", "participants.boots",
                "participants.item0", "participants.item1", "participants.item2", "participants.item3",
                "participants.item4", "participants.item5", "participants.augments"))
            assertEquals(field, Integer.valueOf(1), projection.get(field));
        assertFalse(projection.containsKey("participants"));
    }

    @SuppressWarnings("unchecked")
    private static List<Match> hydrate(List<Document> sources, Map<String, Document> events) throws Exception {
        Method method = MongoDB.class.getDeclaredMethod("championArenaMatches", List.class, Map.class);
        method.setAccessible(true);
        return (List<Match>) method.invoke(null, sources, events);
    }

    private static void assertHydrationFails(Document event) throws Exception {
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
            () -> hydrate(List.of(match("EUW1_5")), Map.of("EUW1_5", event)));
        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    private static Document match(String id) {
        Document first = new Document("id", 1).append("puuid", "player").append("champion", 1)
            .append("win", true).append("subTeamPlacement", 2).append("boots", 3006)
            .append("item0", 447001).append("item1", 4001)
            .append("augments", new ArrayList<>(List.of(101, 0, 103)));
        Document second = new Document("id", 2).append("puuid", "other").append("champion", 2);
        return new Document("_id", id).append("queue", "CHERRY").append("region", "EUW1")
            .append("patch", "26.19.123").append("participants", new ArrayList<>(List.of(first, second)));
    }

    private static String timeline() {
        return "{\"participants\":{\"1\":\"player\",\"2\":\"other\"},\"item_events\":["
            + "{\"event\":\"ITEM_PURCHASED\",\"participant\":1,\"item\":447001,\"before\":0,\"after\":0,\"timestamp\":1000}]}";
    }

    private static Document event(String id, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new Document("_id", id).append("encoding", "json").append("data", json)
            .append("uncompressedBytes", bytes.length)
            .append("checksum", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
