package com.safjnest.lol.service;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

public class ChampionArenaRawFlowTest {
    @Test
    public void rawOnlyColdMatrixAndLaterBuildOnlyMissGenerateArenaThroughTheSameJoin() throws Exception {
        try (Store store = new Store()) {
            Filter filter = Filter.championBuild(27, "16.19", GameQueueType.CHERRY);
            ChampionService service = new ChampionService();
            assertNull(service.getBuild(filter, false));
            service.refreshStatisticsMatrix("16.19", GameQueueType.CHERRY, List.of(filter));
            var statistics = MongoDB.findChampionStatsDocument(ChampionStatsScope.from(filter));
            assertNotNull(statistics);
            assertTrue(statistics.ready);
            assertEquals(1, statistics.champions.get(27).overall().games);
            long statisticsUpdated = statistics.updatedAt;
            Build cold = service.getBuild(filter, false);
            assertEquals(2, cold.games());
            assertTrue(cold.augmentOptions().get(1).isEmpty());
            assertTrue(cold.starterOptions().isEmpty());
            assertEquals(List.of(3006, 447001), cold.coreBuilds().get(0).items());
            assertEquals("66", cold.augmentOptions().get(5).get(0).id());
            assertEquals("4001", cold.itemSlots().get(0).get(0).id());
            assertEquals(1, store.eventQueries);

            store.table("champion_builds").clear();
            assertNull(service.getBuild(filter, false));
            com.safjnest.lol.queue.scheduler.ComputeScheduler.startChampionData(filter, false, true)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
            Build hotStatistics = service.getBuild(filter, false);
            assertEquals(cold.arena(), hotStatistics.arena());
            assertEquals(statisticsUpdated,
                MongoDB.findChampionStatsDocument(ChampionStatsScope.from(filter)).updatedAt);
            assertTrue(MongoDB.findChampionBuildLastUpdate(filter) > 0);
            assertEquals(2, store.eventQueries);

            var converters = new ArrayList<org.springframework.http.converter.HttpMessageConverter<?>>();
            new com.safjnest.spring.config.LolApiConfig().configureMessageConverters(converters);
            var converter = (org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) converters.get(0);
            var json = converter.getObjectMapper().readTree(converter.getObjectMapper().writeValueAsString(hotStatistics));
            assertEquals(2, json.get("games").asInt());
            assertEquals(6, json.get("augments").size());
            assertEquals(0, json.get("starters").size());
            assertEquals(2, json.get("arena").get("stats").get("games").asInt());
            assertFalse(json.has("filter"));
        }
    }

    @Test
    public void standardQueueUsesTheSharedJoinButRetainsItsOwnTwoItemCoreAndTimelineGate() throws Exception {
        try (Store store = new Store()) {
            for (Document match : store.table("match").values()) {
                match.put("queue", "ARAM");
                Document participant = (Document) ((List<?>) match.get("participants")).get(0);
                participant.put("item0", 4001);
                participant.put("item1", 4002);
                participant.put("augments", List.of());
            }
            Document events = store.table("match_events").get("EUW1_1");
            String timeline = events.getString("data").replace("1000}", "180000}").replace("2000}", "240000}");
            byte[] bytes = timeline.getBytes(StandardCharsets.UTF_8);
            events.put("data", timeline);
            events.put("uncompressedBytes", bytes.length);
            events.put("checksum", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            Filter filter = Filter.championBuild(27, "16.19", GameQueueType.ARAM);
            ChampionService service = new ChampionService();
            assertTrue(service.refreshBuild(filter));
            Build build = service.getBuild(filter, false);
            assertEquals(1, build.games());
            assertEquals(List.of(4001, 4002), build.coreBuilds().get(0).items());
            assertNull(build.arena());
            assertEquals(1, store.eventQueries);
        }
    }

    @Test
    public void emptyRawSourceProducesReadyEmptyStatsAndArenaInsteadOfRepeatedMisses() throws Exception {
        try (Store store = new Store()) {
            store.table("match").clear();
            store.table("match_events").clear();
            Filter filter = Filter.championBuild(27, "16.19", GameQueueType.CHERRY);
            ChampionService service = new ChampionService();
            service.refreshStatisticsMatrix("16.19", GameQueueType.CHERRY, List.of(filter));
            assertNotNull(service.getStatistics(filter, false));
            assertEquals(0, service.getStatistics(filter, false).overview().games());
            assertNotNull(service.getBuild(filter, false));
            assertEquals(0, service.getBuild(filter, false).games());
            assertTrue(MongoDB.findChampionBuildLastUpdate(filter) > 0);
        }
    }

    @org.junit.After
    public void stopWorkers() {
        com.safjnest.lol.queue.scheduler.ComputeScheduler.shutdown();
    }

    private static final class Store implements AutoCloseable {
        private final Field database = field(MongoDB.class, "database");
        private final Field ready = field(MongoDB.class, "collectionsReady");
        private final Field redis = field(RedisClient.class, "disabledUntil");
        private final Object previousDatabase;
        private final boolean previousReady;
        private final long previousRedis;
        private final Map<String, Map<String, Document>> tables = new LinkedHashMap<>();
        private final Map<String, Map<Integer, Item>> catalogs;
        private final Map<Integer, Item> previousCatalog;
        private int eventQueries;

        @SuppressWarnings("unchecked")
        private Store() throws Exception {
            previousDatabase = database.get(null);
            previousReady = ready.getBoolean(null);
            previousRedis = redis.getLong(null);
            redis.setLong(null, Long.MAX_VALUE);
            catalogs = (Map<String, Map<Integer, Item>>) field(StaticDataService.class, "PATCH_ITEMS").get(null);
            previousCatalog = catalogs.put("16.19", Map.of(3006, item(3006, "Boots", 2), 4001, item(4001, "Legendary", 3),
                4002, item(4002, "Legendary Two", 3)));
            table("match").put("EUW1_1", match("EUW1_1", "16.19.1"));
            table("match").put("EUW1_2", match("EUW1_2", "16.19.2"));
            String timeline = "{\"participants\":{\"1\":\"player\"},\"item_events\":["
                + "{\"event\":\"ITEM_PURCHASED\",\"participant\":1,\"item\":447001,\"before\":0,\"after\":0,\"timestamp\":1000},"
                + "{\"event\":\"ITEM_PURCHASED\",\"participant\":1,\"item\":4001,\"before\":0,\"after\":0,\"timestamp\":2000}]}";
            byte[] bytes = timeline.getBytes(StandardCharsets.UTF_8);
            table("match_events").put("EUW1_1", new Document("_id", "EUW1_1").append("encoding", "json")
                .append("data", timeline).append("uncompressedBytes", bytes.length)
                .append("checksum", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
            database.set(null, proxy(MongoDatabase.class, (object, method, args) -> {
                if (method.getName().equals("getCollection")) return collection((String) args[0]);
                throw new UnsupportedOperationException(method.getName());
            }));
            ready.setBoolean(null, true);
        }

        private Map<String, Document> table(String name) {
            return tables.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
        }

        private MongoCollection<Document> collection(String name) {
            return proxy(MongoCollection.class, (object, method, args) -> {
                switch (method.getName()) {
                    case "find": {
                        if (name.equals("match_events")) eventQueries++;
                        Document query = bson((Bson) args[0]);
                        List<Document> result = new ArrayList<>();
                        for (Document value : table(name).values()) if (matches(value, query)) result.add(value);
                        return iterable(result);
                    }
                    case "bulkWrite": {
                        for (Object value : (List<?>) args[0]) {
                            var update = (com.mongodb.client.model.UpdateOneModel<?>) value;
                            collection(name).updateOne(update.getFilter(), update.getUpdate(), update.getOptions());
                        }
                        return com.mongodb.bulk.BulkWriteResult.acknowledged(0, 1, 0, 1, List.of(), List.of());
                    }
                    case "countDocuments": {
                        Document query = bson((Bson) args[0]);
                        long count = 0;
                        for (Document value : table(name).values()) if (matches(value, query)) count++;
                        return count;
                    }
                    case "replaceOne": {
                        Document value = (Document) args[1];
                        table(name).put(value.getString("_id"), Document.parse(value.toJson()));
                        return UpdateResult.acknowledged(1, 1L, null);
                    }
                    case "updateOne": {
                        String id = bson((Bson) args[0]).getString("_id");
                        Document update = bson((Bson) args[1]);
                        Document target = table(name).computeIfAbsent(id, ignored -> new Document("_id", id));
                        target.putAll(update.get("$setOnInsert", Document.class));
                        for (var entry : update.get("$set", Document.class).entrySet()) {
                            String[] path = entry.getKey().split("\\.");
                            Document owner = target;
                            for (int i = 0; i < path.length - 1; i++) {
                                if (!owner.containsKey(path[i])) owner.put(path[i], new Document());
                                owner = owner.get(path[i], Document.class);
                            }
                            owner.put(path[path.length - 1], entry.getValue());
                        }
                        return UpdateResult.acknowledged(1, 1L, null);
                    }
                    default: throw new UnsupportedOperationException(name + "." + method.getName());
                }
            });
        }

        private static FindIterable<Document> iterable(List<Document> values) {
            return proxy(FindIterable.class, (object, method, args) -> switch (method.getName()) {
                case "projection", "batchSize" -> object;
                case "first" -> values.isEmpty() ? null : Document.parse(values.get(0).toJson());
                case "iterator" -> {
                    var iterator = values.iterator();
                    yield proxy(MongoCursor.class, (cursor, operation, parameters) -> switch (operation.getName()) {
                        case "hasNext" -> iterator.hasNext();
                        case "next" -> Document.parse(iterator.next().toJson());
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(operation.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static boolean matches(Document value, Document query) {
            for (var entry : query.entrySet()) {
                if (entry.getKey().equals("$and")) {
                    for (Object clause : (List<?>) entry.getValue()) if (!matches(value, (Document) clause)) return false;
                    continue;
                }
                Object actual = value;
                for (String part : entry.getKey().split("\\."))
                    actual = actual instanceof Map<?, ?> map ? map.get(part) : null;
                if (entry.getValue() instanceof Document operation) {
                    if (operation.containsKey("$in")) {
                        if (!((List<?>) operation.get("$in")).contains(actual)) return false;
                    } else if (operation.containsKey("$elemMatch")) {
                        boolean found = false;
                        if (actual instanceof List<?> items) for (Object item : items)
                            if (item instanceof Document document && matches(document, operation.get("$elemMatch", Document.class))) found = true;
                        if (!found) return false;
                    } else return false;
                } else if (!Objects.equals(actual, entry.getValue())) return false;
            }
            return true;
        }

        private static Document match(String id, String patch) {
            Document participant = new Document("id", 1).append("puuid", "player").append("champion", 27)
                .append("lane", "NONE").append("team", "ORDER").append("win", true).append("kda", "1/0/1")
                .append("cs", 0).append("goldEarned", 1000).append("subTeamPlacement", 3).append("boots", 3006)
                .append("item0", 447001).append("augments", java.util.Arrays.asList(11, null, 33, 44, 55, 66));
            return new Document("_id", id).append("queue", "CHERRY").append("patch", patch).append("patchMajor", "16.19")
                .append("region", "EUW1").append("rank", "UNRANKED").append("timeStart", 1000L).append("timeEnd", 601000L)
                .append("participants", List.of(participant));
        }

        private static Item item(int id, String name, int depth) {
            return new Item() {
                public int getId() { return id; }
                public String getName() { return name; }
                public int getDepth() { return depth; }
                public List<String> getFrom() { return List.of(); }
            };
        }

        private static Document bson(Bson value) {
            return Document.parse(value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson());
        }

        private static Field field(Class<?> owner, String name) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

        public void close() throws Exception {
            database.set(null, previousDatabase);
            ready.setBoolean(null, previousReady);
            redis.setLong(null, previousRedis);
            if (previousCatalog == null) catalogs.remove("16.19");
            else catalogs.put("16.19", previousCatalog);
        }
    }
}
