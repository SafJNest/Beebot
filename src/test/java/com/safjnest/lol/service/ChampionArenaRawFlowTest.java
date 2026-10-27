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
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
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
            assertEquals(List.of(4001), cold.arena().coreBuilds().get(0).builds().get(0).items());
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
            assertFalse(json.has("starters"));
            assertFalse(json.has("schemaVersion"));
            assertFalse(json.has("arena"));
            assertFalse(json.has("filter"));
        }
    }

    @Test
    public void forcedMatrixRefreshUpdatesReadyScopesWithNewGamesWithoutDeletingThem() throws Exception {
        try (Store store = new Store()) {
            Filter filter = Filter.championBuild(27, "16.19", GameQueueType.CHERRY);
            ChampionStatsScope scope = ChampionStatsScope.from(filter);
            ChampionService service = new ChampionService();
            service.refreshStatisticsMatrix("16.19", GameQueueType.CHERRY);
            var original = MongoDB.findChampionStatsDocument(scope);
            assertEquals(1, original.games);
            var identities = java.util.Set.copyOf(store.table("champion_stats").keySet());

            store.table("match").put("EUW1_3", Store.match("EUW1_3", "16.19.1"));
            Document events = Document.parse(store.table("match_events").get("EUW1_1").toJson());
            events.put("_id", "EUW1_3");
            store.table("match_events").put("EUW1_3", events);
            var skipped = service.refreshStatisticsMatrix("16.19", GameQueueType.CHERRY);
            assertEquals(skipped.combinations(), skipped.skipped());
            assertEquals(1, MongoDB.findChampionStatsDocument(scope).games);

            var refreshed = service.refreshStatisticsMatrix("16.19", GameQueueType.CHERRY, List.of(), true);
            var updated = MongoDB.findChampionStatsDocument(scope);
            assertEquals(0, refreshed.skipped());
            assertEquals(2, updated.games);
            assertTrue(updated.ready);
            assertEquals(original._id, updated._id);
            assertEquals(identities, store.table("champion_stats").keySet());
            assertEquals(2, store.eventQueries);
        }
    }

    @Test
    public void refreshUsesOnlyPlayedBuildQueuesInTheRequestedPatchAndRunsStatisticsFirst() throws Exception {
        try (Store store = new Store()) {
            Document aram = Store.match("EUW1_3", "16.19.1");
            aram.put("queue", "ARAM");
            store.table("match").put("EUW1_3", aram);
            Document unsupported = Store.match("EUW1_4", "16.19.1");
            unsupported.put("queue", "STRAWBERRY");
            store.table("match").put("EUW1_4", unsupported);
            Document otherPatch = Store.match("EUW1_5", "16.20.1");
            otherPatch.put("patchMajor", "16.20");
            otherPatch.put("queue", "RANKED_FLEX_SR");
            store.table("match").put("EUW1_5", otherPatch);
            assertEquals(java.util.Set.of(GameQueueType.CHERRY, GameQueueType.ARAM, GameQueueType.STRAWBERRY),
                java.util.Set.copyOf(MongoDB.findChampionQueues("16.19")));
            List<String> phases = new ArrayList<>();
            Job<Void> job = new Job<>(1, 0, ComputeScheduler.class, DatabaseWorkerType.CHAMPION,
                "refresh", "refresh", JobPriority.BACKGROUND, ignored -> null);
            ChampionService service = new ChampionService() {
                @Override
                public MatrixRefreshResult refreshStatisticsMatrix(String patch, GameQueueType queue,
                        List<Filter> builds, boolean force) {
                    assertEquals("16.19", patch);
                    assertTrue(force);
                    assertTrue(builds.isEmpty());
                    assertEquals("STATISTICS", job.phase());
                    phases.add("stats:" + queue);
                    return new MatrixRefreshResult(1, 0, 1, 0, 1);
                }

                @Override
                public boolean refreshBuild(Filter filter) {
                    assertEquals("16.19", filter.patch());
                    assertEquals("BUILDS", job.phase());
                    assertTrue(phases.containsAll(List.of("stats:CHERRY", "stats:ARAM")));
                    phases.add("build:" + filter.queue());
                    return true;
                }
            };
            service.refresh("16.19", job);
            assertEquals(java.util.Set.of("stats:CHERRY", "stats:ARAM", "build:CHERRY", "build:ARAM"),
                java.util.Set.copyOf(phases));
            assertEquals(4, job.progress().total());
            assertEquals(4, job.progress().current());
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
                        Document query = args == null || args.length == 0 ? new Document() : bson((Bson) args[0]);
                        List<Document> result = new ArrayList<>();
                        for (Document value : table(name).values()) if (matches(value, query)) result.add(value);
                        return iterable(result);
                    }
                    case "distinct": {
                        Document query = bson((Bson) args[1]);
                        var values = new java.util.LinkedHashSet<String>();
                        for (Document value : table(name).values())
                            if (matches(value, query)) values.add(value.getString((String) args[0]));
                        return proxy(com.mongodb.client.DistinctIterable.class, (object2, operation, parameters) -> {
                            if (!operation.getName().equals("into")) throw new UnsupportedOperationException(operation.getName());
                            ((java.util.Collection<String>) parameters[0]).addAll(values);
                            return parameters[0];
                        });
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
                .append("item0", 447001).append("item1", 4001).append("augments", java.util.Arrays.asList(11, null, 33, 44, 55, 66));
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
