package com.safjnest.nosql;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.lol.model.ArenaBuildData;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.service.ArenaChampionAnalyzer;
import com.safjnest.utils.JsonCodec;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class MongoChampionArenaPersistenceTest {

    @Test
    public void standardThenArenaAndStandardRefreshPreserveBothPopulations() throws Exception {
        try (Store store = new Store()) {
            Build standard = standard(4);
            String original = JsonCodec.toJson(standard);
            assertTrue(MongoDB.upsertChampionBuild(standard));
            Object updatedAt = store.saved.get("lastUpdate");
            Object games = store.saved.get("games");
            assertTrue(MongoDB.upsertChampionArena(filter(), arena()));
            assertEquals(filter().toKey(), store.saved.getString("_id"));
            assertEquals(filter().toKey(), store.saved.getString("filterKey"));
            assertEquals(updatedAt, store.saved.get("lastUpdate"));
            assertEquals(games, store.saved.get("games"));
            assertEquals(arena(), MongoDB.findChampionArena(filter()));
            assertEquals(original, JsonCodec.toJson(MongoDB.findChampionBuilds(filter()).get(0)));
            assertNull(MongoDB.findChampionBuilds(filter()).get(0).arena());
            assertTrue(MongoDB.upsertChampionBuild(standard(7)));
            assertEquals(7, MongoDB.findChampionBuilds(filter()).get(0).games());
            assertEquals(arena(), MongoDB.findChampionArena(filter()));
            assertFalse(store.updates.get(1).get("$set", Document.class).containsKey("build"));
            assertEquals(List.of("build.arena"), new ArrayList<>(store.updates.get(1).get("$set", Document.class).keySet()));
        }
    }

    @Test
    public void arenaFirstDoesNotMarkStandardReadyAndBulkStandardKeepsArena() throws Exception {
        try (Store store = new Store()) {
            assertTrue(MongoDB.upsertChampionArena(filter(), arena()));
            assertFalse(store.saved.containsKey("buildVersion"));
            assertFalse(store.saved.containsKey("games"));
            assertFalse(store.saved.containsKey("lastUpdate"));
            assertTrue(MongoDB.findChampionBuilds(filter()).isEmpty());
            assertTrue(MongoDB.upsertChampionBuilds(List.of(standard(5))));
            assertEquals(arena(), MongoDB.findChampionArena(filter()));
            assertEquals(5, MongoDB.findChampionBuilds(filter()).get(0).games());
            Document update = store.updates.get(1);
            assertFalse(update.get("$set", Document.class).containsKey("build"));
            assertFalse(update.get("$set", Document.class).containsKey("build.arena"));
        }
    }

    @Test
    public void combinedEnvelopeActuallyRoundTripsThroughBsonAndJson() throws Exception {
        try (Store store = new Store()) {
            MongoDB.upsertChampionBuild(standard(4));
            MongoDB.upsertChampionArena(filter(), arena());
            Document decoded = bsonRoundTrip(store.saved);
            Build combined = JsonCodec.fromDocument(decoded.get("build"), Build.class);
            assertNotNull(combined);
            assertEquals(4, combined.games());
            assertEquals(arena(), combined.arena());
            assertEquals(arena(), JsonCodec.fromJson(JsonCodec.toJson(combined), Build.class).arena());
            assertEquals(filter().toKey(), decoded.getString("filterKey"));
        }
    }

    @Test
    public void malformedAndObsoleteArenaCannotBreakTheStandardReader() throws Exception {
        try (Store store = new Store()) {
            MongoDB.upsertChampionBuild(standard(4));
            Document payload = store.saved.get("build", Document.class);
            payload.put("arena", new Document("cores", "invalid").append("schemaVersion", 2));
            assertEquals(4, MongoDB.findChampionBuilds(filter()).get(0).games());
            assertNull(MongoDB.findChampionArena(filter()));
            assertTrue(payload.containsKey("arena"));
            ArenaBuildData current = arena();
            payload.put("arena", JsonCodec.toDocument(new ArenaBuildData(2, 3, current.stats(), current.cores(),
                current.prismatics(), current.augments(), current.coverage())));
            assertNull(MongoDB.findChampionArena(filter()));
            assertEquals(4, MongoDB.findChampionBuilds(filter()).get(0).games());
        }
    }

    @Test
    public void unsafeScopesVersionsAndAmbiguousStandardBuildNeverWrite() throws Exception {
        try (Store store = new Store()) {
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionArena(filter().setOpponent(1), arena()));
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionArena(filter().setPatch("16.19"), arena()));
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionArena(filter().setPeriod(1, 2), arena()));
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionArena(filter().setRankBehavior(Filter.RankBehavior.EXACT), arena()));
            ArenaBuildData current = arena();
            ArenaBuildData unsupported = new ArenaBuildData(3, 2, current.stats(), current.cores(),
                current.prismatics(), current.augments(), current.coverage());
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionArena(filter(), unsupported));
            Build source = standard(4);
            Build ambiguous = new Build(source.filter(), source.games(), source.wins(), source.winrate(), source.coreBuilds(),
                source.coreItems(), source.starterOptions(), source.bootOptions(), source.supportItemOptions(),
                source.roleBoundItemOptions(), source.itemSlots(), source.runeOptions(), source.summonerSpellOptions(),
                source.skillOrders(), source.prismaticOptions(), source.augmentOptions(), current);
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionBuild(ambiguous));
            assertThrows(IllegalArgumentException.class, () -> MongoDB.upsertChampionBuilds(List.of(ambiguous)));
            assertTrue(store.updates.isEmpty());
        }
    }

    @Test
    public void unacknowledgedAndSimulatedServerLimitErrorsPropagate() throws Exception {
        try (Store store = new Store()) {
            MongoDB.upsertChampionBuild(standard(4));
            MongoDB.upsertChampionArena(filter(), arena());
            Document previous = cloneDocument(store.saved);
            store.reject = true;
            assertThrows(IllegalStateException.class, () -> MongoDB.upsertChampionArena(filter(), arena()));
            assertEquals(previous, store.saved);
            store.reject = false;
            store.acknowledge = false;
            assertThrows(IllegalStateException.class, () -> MongoDB.upsertChampionArena(filter(), arena()));
            assertThrows(IllegalStateException.class, () -> MongoDB.upsertChampionBuild(standard(4)));
            assertThrows(IllegalStateException.class, () -> MongoDB.upsertChampionBuilds(List.of(standard(4))));
        }
    }

    @Test
    public void interleavedDisjointWritesRequireNoReadsAndLoseNoPopulation() throws Exception {
        try (Store store = new Store()) {
            Thread standard = Thread.ofPlatform().start(() -> {
                for (int i = 1; i <= 10; i++) MongoDB.upsertChampionBuild(standard(i));
            });
            Thread arena = Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 10; i++) MongoDB.upsertChampionArena(filter(), arena());
            });
            standard.join(); arena.join();
            assertEquals(20, store.updates.size());
            assertEquals(0, store.reads);
            assertEquals(10, store.saved.getInteger("games").intValue());
            assertEquals(arena(), MongoDB.findChampionArena(filter()));
        }
    }

    private static Filter filter() { return Filter.championBuild(27, "16.19.1", GameQueueType.CHERRY); }

    private static Build standard(int games) {
        return new Build(filter(), games, games, 1, List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ArenaBuildData arena() {
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19.1", CATALOG);
        var p = participant(1, 3); p.boots = 3006; p.item0 = 447001; p.item1 = 4001; p.augments = List.of(11);
        accumulator.accept(match("EUW1_1", p, List.of()));
        return accumulator.finish();
    }

    private static Document bsonRoundTrip(Document value) {
        var codec = MongoClientSettings.getDefaultCodecRegistry().get(Document.class);
        try (BasicOutputBuffer output = new BasicOutputBuffer(); BsonBinaryWriter writer = new BsonBinaryWriter(output)) {
            codec.encode(writer, value, EncoderContext.builder().build());
            try (BsonBinaryReader reader = new BsonBinaryReader(java.nio.ByteBuffer.wrap(output.toByteArray()))) {
                return codec.decode(reader, DecoderContext.builder().build());
            }
        }
    }

    private static Document cloneDocument(Document value) { return Document.parse(value.toJson()); }

    private static final class Store implements AutoCloseable {
        private final Field databaseField;
        private final Field readyField;
        private final Object originalDatabase;
        private final boolean originalReady;
        private final List<Document> updates = new ArrayList<>();
        private Document saved;
        private boolean reject;
        private boolean acknowledge = true;
        private int reads;

        private Store() throws Exception {
            databaseField = MongoDB.class.getDeclaredField("database"); databaseField.setAccessible(true);
            readyField = MongoDB.class.getDeclaredField("collectionsReady"); readyField.setAccessible(true);
            originalDatabase = databaseField.get(null); originalReady = readyField.getBoolean(null);
            MongoCollection<Document> collection = proxy(MongoCollection.class, this::collection);
            MongoDatabase database = proxy(MongoDatabase.class, (object, method, args) -> {
                if (method.getName().equals("getCollection")) return collection;
                throw new UnsupportedOperationException(method.getName());
            });
            databaseField.set(null, database); readyField.setBoolean(null, true);
        }

        private synchronized Object collection(Object object, Method method, Object[] args) {
            return switch (method.getName()) {
                case "updateOne" -> {
                    assertTrue(((UpdateOptions) args[2]).isUpsert());
                    apply((org.bson.conversions.Bson) args[0], (org.bson.conversions.Bson) args[1]);
                    yield acknowledge ? UpdateResult.acknowledged(1, 1L, null) : UpdateResult.unacknowledged();
                }
                case "bulkWrite" -> {
                    for (Object operation : (List<?>) args[0]) {
                        assertTrue(operation instanceof UpdateOneModel<?>);
                        UpdateOneModel<?> update = (UpdateOneModel<?>) operation;
                        assertTrue(update.getOptions().isUpsert());
                        apply(update.getFilter(), update.getUpdate());
                    }
                    yield acknowledge ? BulkWriteResult.acknowledged(0, 1, 0, 1, List.of(), List.of()) : BulkWriteResult.unacknowledged();
                }
                case "getNamespace" -> new MongoNamespace("isolated_fake", "champion_builds");
                case "find" -> {
                    reads++;
                    Document query = bson((org.bson.conversions.Bson) args[0]);
                    boolean match = saved != null && matches(query);
                    yield iterable(match ? List.of(saved) : List.of());
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private void apply(org.bson.conversions.Bson filter, org.bson.conversions.Bson update) {
            Document change = bson(update);
            assertEquals(List.of("$set", "$setOnInsert"), new ArrayList<>(change.keySet()));
            Document candidate = saved == null ? new Document("_id", bson(filter).get("_id")) : cloneDocument(saved);
            if (saved == null) candidate.putAll(change.get("$setOnInsert", Document.class));
            for (var entry : change.get("$set", Document.class).entrySet()) {
                String[] path = entry.getKey().split("\\.");
                Document owner = candidate;
                for (int i = 0; i < path.length - 1; i++) {
                    if (!owner.containsKey(path[i])) owner.put(path[i], new Document());
                    owner = owner.get(path[i], Document.class);
                }
                owner.put(path[path.length - 1], entry.getValue());
            }
            if (reject) throw new IllegalStateException("Simulated server final-document size rejection");
            saved = candidate; updates.add(change);
        }

        private boolean matches(Document query) {
            if (query.get("$and") instanceof List<?> conditions) {
                for (Object condition : conditions) if (!matches((Document) condition)) return false;
                return true;
            }
            for (var condition : query.entrySet()) if (!java.util.Objects.equals(saved.get(condition.getKey()), condition.getValue())) return false;
            return true;
        }

        private static FindIterable<Document> iterable(List<Document> documents) {
            return proxy(FindIterable.class, (object, method, args) -> switch (method.getName()) {
                case "projection" -> object;
                case "first" -> documents.isEmpty() ? null : documents.get(0);
                case "iterator" -> {
                    var iterator = documents.iterator();
                    yield proxy(MongoCursor.class, (cursor, cursorMethod, cursorArgs) -> switch (cursorMethod.getName()) {
                        case "hasNext" -> iterator.hasNext();
                        case "next" -> iterator.next();
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(cursorMethod.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static Document bson(org.bson.conversions.Bson value) {
            return Document.parse(value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson());
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        }

        @Override
        public void close() throws Exception {
            databaseField.set(null, originalDatabase); readyField.setBoolean(null, originalReady);
        }
    }
}
