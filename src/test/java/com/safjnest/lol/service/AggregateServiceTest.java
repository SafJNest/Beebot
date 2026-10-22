package com.safjnest.lol.service;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.job.JobState;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.status.LeagueMetricsStore;

public class AggregateServiceTest {

    @Test
    public void coldRedisRestoresZeroAndEmptySnapshotsWithoutCallingTheSource() throws Exception {
        try (Store store = new Store()) {
            Object[] scope = {UUID.randomUUID().toString(), "HIGHEST_MASTERY", "GLOBAL", "all"};
            AggregateService.put(RedisKey.RECORD_COUNT, 0L, scope);
            assertEquals(Long.valueOf(0), AggregateService.get(RedisKey.RECORD_COUNT, Long.class,
                () -> { throw new AssertionError("Stored zero must be reused"); }, scope));
            AggregateService.put(RedisKey.STATUS_RANKS_BY_QUEUE, Map.of());
            assertEquals(Map.of(), AggregateService.get(RedisKey.STATUS_RANKS_BY_QUEUE,
                new TypeReference<Map<String, Long>>() {}, () -> { throw new AssertionError("Stored empty map must be reused"); }));
            assertEquals(2, store.writes.get());
        }
    }

    @Test
    public void firstCalculationPersistsTheExactIdentityAndCanonicalArgumentsOnce() throws Exception {
        try (Store store = new Store()) {
            AtomicInteger calculations = new AtomicInteger();
            Object[] scope = {UUID.randomUUID().toString(), "HIGHEST_MASTERY", "EUW1", 27};
            for (int attempt = 0; attempt < 2; attempt++)
                assertEquals(Long.valueOf(17), AggregateService.get(RedisKey.RECORD_COUNT, Long.class,
                    () -> { calculations.incrementAndGet(); return 17L; }, scope));
            Document saved = store.snapshots.get(RedisKey.RECORD_COUNT.of(scope));
            assertEquals("RECORD_COUNT", saved.getString("type"));
            assertEquals(List.of(scope[0], "HIGHEST_MASTERY", "EUW1", "27"), saved.get("arguments"));
            assertTrue(saved.get("value") instanceof Number);
            assertTrue(((Number) saved.get("updatedAt")).longValue() > 0);
            assertEquals(1, calculations.get());
            assertEquals(1, store.writes.get());
            assertFalse(RedisKey.RECORD_COUNT.of(scope).equals(
                RedisKey.RECORD_COUNT.of(scope[0], "HIGHEST_MASTERY", "GLOBAL", 27)));
        }
    }

    @Test
    public void sourceAndPersistenceFailuresDoNotReplaceAValidSnapshotWithZero() throws Exception {
        try (Store store = new Store()) {
            Object[] scope = {UUID.randomUUID().toString(), "DEATHS", "GLOBAL", "all"};
            assertThrows(IllegalStateException.class, () -> AggregateService.get(RedisKey.RECORD_COUNT, Long.class,
                () -> { throw new IllegalStateException("source failed"); }, scope));
            assertFalse(store.snapshots.containsKey(RedisKey.RECORD_COUNT.of(scope)));
            AggregateService.put(RedisKey.RECORD_COUNT, 7L, scope);
            store.rejectWrites = true;
            assertThrows(IllegalStateException.class, () -> AggregateService.put(RedisKey.RECORD_COUNT, 0L, scope));
            assertEquals(7L, ((Number) store.snapshots.get(RedisKey.RECORD_COUNT.of(scope)).get("value")).longValue());
            store.failSources = true;
            assertThrows(IllegalStateException.class, () -> AggregateService.get(RedisKey.STATUS_TOTAL_MASTERIES,
                Long.class, () -> (Long) LeagueMetricsStore.calculate(RedisKey.STATUS_TOTAL_MASTERIES)));
            assertFalse(store.snapshots.containsKey(RedisKey.STATUS_TOTAL_MASTERIES.of()));
        }
    }

    @Test
    public void incrementsRequireACompleteSnapshotAndInvalidationRemovesTheDurableCount() throws Exception {
        try (Store store = new Store()) {
            String queue = UUID.randomUUID().toString();
            AggregateService.incrementHash(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, "GOLD_IV", 1, queue, "GLOBAL");
            assertTrue(store.snapshots.isEmpty());
            AggregateService.put(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, Map.of("GOLD_IV", 3L, "GOLD_III", 2L), queue, "GLOBAL");
            AggregateService.incrementHash(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, "GOLD_IV", -1, queue, "GLOBAL");
            assertEquals(Map.of("GOLD_IV", 2L, "GOLD_III", 2L),
                AggregateService.findHash(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, queue, "GLOBAL"));
            String id = RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(queue, "GLOBAL");
            AggregateService.invalidate(List.of(id));
            assertNull(AggregateService.findHash(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, queue, "GLOBAL"));
            assertFalse(store.snapshots.containsKey(id));
        }
    }

    @Test
    public void recordRegenerationRecalculatesGroupedScopesAndZeroesDisappearedScopes() throws Exception {
        try (Store store = new Store()) {
            String filter = UUID.randomUUID().toString();
            AggregateService.put(RedisKey.RECORD_COUNT, 99L, filter, "HIGHEST_MASTERY", "EUW1", "27");
            AggregateService.put(RedisKey.RECORD_COUNT, 99L, filter, "DEATHS", "KR", "all");
            store.recordGroups.add(new Document("_id", new Document("filterKey", filter).append("metric", "HIGHEST_MASTERY")
                .append("region", "EUW1").append("championId", 27)).append("count", 3L));
            store.recordGroups.add(new Document("_id", new Document("filterKey", filter).append("metric", "HIGHEST_MASTERY")
                .append("region", "KR").append("championId", 27)).append("count", 2L));
            Job<Void> job = new Job<>(1, 0, ComputeScheduler.class, DatabaseWorkerType.MONGO, "records", "records",
                JobPriority.BACKGROUND, ignored -> null);
            var rebuild = AggregateService.class.getDeclaredMethod("rebuild", String.class, Job.class);
            rebuild.setAccessible(true);
            rebuild.invoke(null, "record-counts", job);
            assertCount(store, filter, "HIGHEST_MASTERY", "GLOBAL", "27", 5);
            assertCount(store, filter, "HIGHEST_MASTERY", "GLOBAL", "all", 5);
            assertCount(store, filter, "HIGHEST_MASTERY", "EUW1", "27", 3);
            assertCount(store, filter, "DEATHS", "KR", "all", 0);
            assertEquals(1, store.recordScans.get());
            assertEquals(new JobProgress(7, 7), job.progress());
            assertEquals("DONE total=7 completed=7 missing=0 failed=0", job.phase());
        }
    }

    @Test
    public void sixQueuedTypesKeepTheirParentAliveAndDeduplicateWithoutBlockingItsWorker() throws Exception {
        try (Store store = new Store()) {
            store.discoveryEntered = new CountDownLatch(1);
            store.discoveryRelease = new CountDownLatch(1);
            CompletableFuture<Void> parent = QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO,
                "aggregate-test-parent", "aggregate test parent", job -> { AggregateService.enqueueRebuild("all"); return null; });
            try {
                assertTrue(store.discoveryEntered.await(5, TimeUnit.SECONDS));
                assertFalse(parent.isDone());
                assertTrue(QueueHandler.snapshot().stream().anyMatch(job -> job.key().equals("aggregate-test-parent")
                    && job.state() == JobState.WAITING_CHILDREN && job.progress().total() == 6));
                AggregateService.enqueueRebuild("all");
                for (String type : AggregateService.types()) {
                    var jobs = QueueHandler.snapshot().stream().filter(job -> job.key().equals("aggregate-rebuild:" + type)).toList();
                    assertEquals(1, jobs.stream().filter(job -> job.followingPid() == null).count());
                    assertEquals(1, jobs.stream().filter(job -> job.followingPid() != null).count());
                }
            } finally {
                store.discoveryRelease.countDown();
                parent.get(10, TimeUnit.SECONDS);
                ComputeScheduler.shutdown();
            }
        }
    }

    private static void assertCount(Store store, String filter, String metric, String region, String champion, long expected) {
        assertEquals(expected, ((Number) store.snapshots.get(RedisKey.RECORD_COUNT.of(filter, metric, region, champion)).get("value")).longValue());
    }

    private static final class Store implements AutoCloseable {
        private final Field database = field(MongoDB.class, "database");
        private final Field ready = field(MongoDB.class, "collectionsReady");
        private final Field redisDisabled = field(RedisClient.class, "disabledUntil");
        private final Object previousDatabase;
        private final boolean previousReady;
        private final long previousRedisDisabled;
        private final Map<String, Document> snapshots = new ConcurrentHashMap<>();
        private final List<Document> recordGroups = new ArrayList<>();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger recordScans = new AtomicInteger();
        private boolean rejectWrites;
        private boolean failSources;
        private CountDownLatch discoveryEntered;
        private CountDownLatch discoveryRelease;

        private Store() throws Exception {
            previousDatabase = database.get(null);
            previousReady = ready.getBoolean(null);
            previousRedisDisabled = redisDisabled.getLong(null);
            redisDisabled.setLong(null, Long.MAX_VALUE);
            database.set(null, proxy(MongoDatabase.class, (object, method, args) -> {
                if (!method.getName().equals("getCollection")) throw new UnsupportedOperationException(method.getName());
                String collection = (String) args[0];
                return proxy(MongoCollection.class, (ignored, operation, values) -> switch (operation.getName()) {
                    case "find" -> {
                        Document filter = bson((Bson) values[0]);
                        if (filter.containsKey("type") && discoveryRelease != null) {
                            discoveryEntered.countDown();
                            assertTrue(discoveryRelease.await(5, TimeUnit.SECONDS));
                        }
                        List<Document> rows = snapshots.values().stream().filter(row -> matches(row, filter)).toList();
                        yield iterable(FindIterable.class, collection.equals("aggregates") ? rows : List.of());
                    }
                    case "replaceOne" -> {
                        if (rejectWrites) yield UpdateResult.unacknowledged();
                        Document saved = (Document) values[1];
                        snapshots.put(saved.getString("_id"), saved);
                        writes.incrementAndGet();
                        yield UpdateResult.acknowledged(1, 1L, null);
                    }
                    case "deleteOne" -> {
                        snapshots.remove(bson((Bson) values[0]).getString("_id"));
                        yield DeleteResult.acknowledged(1);
                    }
                    case "findOneAndUpdate" -> {
                        Document filter = bson((Bson) values[0]);
                        String id = filter.getList("$and", Document.class).get(0).getString("_id");
                        Document saved = snapshots.get(id);
                        if (saved == null) yield null;
                        Document update = bson((Bson) values[1]);
                        Document counts = saved.get("value", Document.class);
                        for (var change : update.get("$inc", Document.class).entrySet()) {
                            String name = change.getKey().substring("value.".length());
                            if (!counts.containsKey(name)) yield null;
                            counts.put(name, ((Number) counts.get(name)).longValue() + ((Number) change.getValue()).longValue());
                        }
                        yield saved;
                    }
                    case "aggregate" -> {
                        if (failSources) throw new IllegalStateException("source failed");
                        if (collection.equals("profile_records")) recordScans.incrementAndGet();
                        yield iterable(AggregateIterable.class, collection.equals("profile_records") ? recordGroups : List.of());
                    }
                    case "estimatedDocumentCount", "countDocuments" -> 0L;
                    default -> throw new UnsupportedOperationException(operation.getName());
                });
            }));
            ready.setBoolean(null, true);
        }

        private static boolean matches(Document row, Document filter) {
            if (filter.containsKey("_id")) return filter.get("_id").equals(row.get("_id"));
            if (filter.containsKey("type")) return filter.get("type", Document.class).getList("$in", String.class).contains(row.getString("type"));
            return true;
        }

        private static Object iterable(Class<?> type, List<Document> rows) {
            return proxy(type, (object, method, args) -> switch (method.getName()) {
                case "first" -> rows.isEmpty() ? null : rows.get(0);
                case "projection", "batchSize" -> object;
                case "into" -> { ((List<Document>) args[0]).addAll(rows); yield args[0]; }
                case "iterator" -> {
                    var iterator = rows.iterator();
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

        private static Field field(Class<?> type, String name) {
            try {
                Field field = type.getDeclaredField(name);
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

        @Override
        public void close() throws Exception {
            database.set(null, previousDatabase);
            ready.setBoolean(null, previousReady);
            redisDisabled.setLong(null, previousRedisDisabled);
            ((java.util.Set<?>) field(AggregateService.class, "UNPUBLISHED").get(null)).clear();
        }
    }
}
