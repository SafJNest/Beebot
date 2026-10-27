package com.safjnest.spring.config;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchPage;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class MatchApiContractTest {

    @Test
    public void listReturnsFullMatchesAndBansWithoutReadingTimeline() throws Exception {
        try (Store store = new Store()) {
            List<Match> matches = MongoDB.findMatchPage("player", filter(), 2, 21, true);
            ObjectMapper mapper = apiMapper();
            ObjectNode row = (ObjectNode) mapper.readTree(mapper.writeValueAsString(
                new MatchPage(matches, 20, 2, 40, true))).path("items").get(0);

            assertEquals(List.of("match"), store.reads);
            assertEquals(2, store.offset);
            assertEquals(21, store.limit);
            assertEquals(new Document("timeStart", 1), store.sort);
            assertTrue(store.query.toJson().contains("player"));
            assertTrue(store.query.toJson().contains("EUW1"));
            assertEquals("EUW1_123", row.path("gameId").asText());
            assertEquals("16.20.1", row.path("patch").asText());
            assertEquals(157, row.path("bans").path("BLUE").get(0).asInt());
            assertEquals(238, row.path("bans").path("RED").get(0).asInt());
            assertEquals(2, row.path("participants").size());
            var player = row.path("participants").get(0);
            for (Field field : Participant.class.getFields()) assertTrue(field.getName(), player.has(field.getName()));
            assertEquals(1, player.path("id").asInt());
            assertEquals(18, player.path("championLevel").asInt());
            assertEquals(2, player.path("subTeam").asInt());
            assertEquals(1, player.path("subTeamPlacement").asInt());
            assertEquals(101, player.path("augments").get(0).asInt());
            assertEquals(2, player.path("skillOrder").size());
            assertEquals(1055, player.path("starterItems").get(0).asInt());
            assertEquals(3031, player.path("buildPath").get(0).asInt());
            assertEquals(3006, player.path("boots").asInt());
            assertEquals(3877, player.path("supportItem").asInt());
            assertEquals(12000, player.path("damageTaken").asInt());
            assertEquals(2000, player.path("healing").asInt());
            assertEquals(21, player.path("rankProgress").path("gain").asInt());
            for (String field : List.of("win", "kda", "championId", "items", "teamKills", "events", "dirty"))
                assertFalse(field, row.has(field));
        }
    }

    @Test
    public void detailAddsTheEntireStoredTimelineToTheSameMatch() throws Exception {
        try (Store store = new Store()) {
            ObjectMapper mapper = apiMapper();
            Match row = MongoDB.findMatchPage("player", filter(), 0, 1, false).get(0);
            Match detail = MongoDB.findMatch("EUW1_123");
            ObjectNode listJson = (ObjectNode) mapper.readTree(mapper.writeValueAsString(row));
            ObjectNode detailJson = (ObjectNode) mapper.readTree(mapper.writeValueAsString(detail));

            assertEquals(List.of("match", "match", "match_events"), store.reads);
            assertEquals(mapper.readTree(store.timeline), detailJson.remove("events"));
            assertEquals(listJson, detailJson);
            assertEquals("player", detail.events.getJSONObject("participants").getString("1"));
            assertEquals(60000, detail.events.getJSONArray("item_events").getJSONObject(0).getLong("timestamp"));
            assertEquals(2, detail.events.getJSONArray("skill_events").getJSONObject(0).getInt("skill_slot"));
        }
    }

    @Test
    public void missingTimelineKeepsTheFullMatchAndCorruptTimelineFailsExplicitly() throws Exception {
        try (Store store = new Store()) {
            store.event = null;
            Match detail = MongoDB.findMatch("EUW1_123");
            assertEquals(List.of(101, 102), detail.participants.get(0).augments);
            assertEquals(List.of(157), detail.bans.get(no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE));
            assertTrue(apiMapper().readTree(apiMapper().writeValueAsString(detail)).path("events").isObject());
            assertTrue(detail.eventData.isEmpty());

            store.event = store.eventDocument();
            store.event.put("checksum", "corrupt");
            assertThrows(IllegalStateException.class, () -> MongoDB.findMatch("EUW1_123"));
            assertEquals(1, MongoDB.findMatchPage("player", filter(), 0, 1, false).size());
        }
    }

    private static Filter filter() {
        return Filter.summoner(0, 0).setRegion(LeagueShard.EUW1);
    }

    private static ObjectMapper apiMapper() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new LolApiConfig().configureMessageConverters(converters);
        return ((MappingJackson2HttpMessageConverter) converters.get(0)).getObjectMapper();
    }

    private static Document matchDocument() {
        Document player = new Document("id", 1).append("puuid", "player").append("riotId", "Player")
            .append("riotTag", "EUW").append("champion", 82).append("championLevel", 18)
            .append("win", true).append("kda", "6/13/11").append("team", "BLUE").append("lane", "NONE")
            .append("subTeam", 2).append("subTeamPlacement", 1).append("damageTaken", 12000).append("healing", 2000)
            .append("rankProgress", new Document("rank", "DIAMOND_II").append("lp", 74).append("gain", 21))
            .append("item0", 3031).append("summonerSpell1", 4).append("summonerSpell2", 14)
            .append("primaryRunes", List.of(8000, 8010)).append("secondaryRunes", List.of(8300, 8347))
            .append("statsRunes", List.of(5005, 5008, 5001)).append("skillOrder", List.of(1, 2))
            .append("augments", List.of(101, 102)).append("starterItems", List.of(1055))
            .append("buildPath", List.of(3031, 6673)).append("boots", 3006).append("supportItem", 3877);
        return new Document("_id", "EUW1_123").append("region", "EUW1").append("queue", "CHERRY")
            .append("rank", "DIAMOND").append("patch", "16.20.1").append("lastUpdate", 300000L)
            .append("timeStart", 100000L).append("timeEnd", 200000L)
            .append("bans", new Document("BLUE", List.of(157)).append("RED", List.of(238)))
            .append("participants", List.of(player, new Document("id", 2).append("puuid", "other").append("champion", 412)));
    }

    private static final class Store implements AutoCloseable {
        private final Field databaseField;
        private final Field readyField;
        private final Object originalDatabase;
        private final boolean originalReady;
        private final List<String> reads = new ArrayList<>();
        private final String timeline = """
            {"participants":{"1":"player","2":"other"},
             "item_events":[{"event":"ITEM_PURCHASED","participant":1,"item":3031,"timestamp":60000}],
             "skill_events":[{"participant":1,"skill_slot":2,"timestamp":30000}],
             "level_events":[{"participant":1,"level":2,"timestamp":30000}],
             "champion_kills":[{"killer":1,"victim":2,"assists":[],"timestamp":90000}],
             "monster_events":[{"monster":"DRAGON","killer":1,"timestamp":95000}],
             "building_events":[{"building":"TOWER_BUILDING","timestamp":96000}],
             "ward_events":[],"turret_plate_events":[],"objective_events":[],"game_events":[],
             "snapshots":[{"timestamp":100000,"participants":{"1":{"total_gold":6000}}}],
             "dragon_soul":null}
            """;
        private Document event;
        private Document query;
        private Document sort;
        private int offset;
        private int limit;

        private Store() throws Exception {
            databaseField = MongoDB.class.getDeclaredField("database");
            databaseField.setAccessible(true);
            readyField = MongoDB.class.getDeclaredField("collectionsReady");
            readyField.setAccessible(true);
            originalDatabase = databaseField.get(null);
            originalReady = readyField.getBoolean(null);
            event = eventDocument();
            MongoDatabase database = proxy(MongoDatabase.class, (object, method, args) -> {
                if (!method.getName().equals("getCollection")) throw new UnsupportedOperationException(method.getName());
                String collection = (String) args[0];
                return proxy(MongoCollection.class, (owner, operation, values) -> {
                    if (!operation.getName().equals("find")) throw new UnsupportedOperationException(operation.getName());
                    reads.add(collection);
                    query = bson((Bson) values[0]);
                    return iterable(collection.equals("match") ? List.of(matchDocument()) : event == null ? List.of() : List.of(event));
                });
            });
            databaseField.set(null, database);
            readyField.setBoolean(null, true);
        }

        private Document eventDocument() throws Exception {
            byte[] bytes = timeline.getBytes(StandardCharsets.UTF_8);
            return new Document("_id", "EUW1_123").append("encoding", "json").append("data", timeline)
                .append("uncompressedBytes", bytes.length)
                .append("checksum", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }

        private FindIterable<Document> iterable(List<Document> documents) {
            return proxy(FindIterable.class, (object, method, args) -> switch (method.getName()) {
                case "sort" -> { sort = bson((Bson) args[0]); yield object; }
                case "skip" -> { offset = (int) args[0]; yield object; }
                case "limit" -> { limit = (int) args[0]; yield object; }
                case "first" -> documents.isEmpty() ? null : documents.get(0);
                case "iterator" -> {
                    var iterator = documents.iterator();
                    yield proxy(MongoCursor.class, (cursor, operation, values) -> switch (operation.getName()) {
                        case "hasNext" -> iterator.hasNext();
                        case "next" -> iterator.next();
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(operation.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static Document bson(Bson value) {
            return Document.parse(value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson());
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        }

        @Override
        public void close() throws Exception {
            databaseField.set(null, originalDatabase);
            readyField.setBoolean(null, originalReady);
        }
    }
}
