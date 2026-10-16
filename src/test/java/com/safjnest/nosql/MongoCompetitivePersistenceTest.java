package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.Document;
import org.junit.Test;

import com.mongodb.MongoNamespace;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.utils.LeagueConstants;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class MongoCompetitivePersistenceTest {

    @Test
    public void upsertWritesScoreOnlyToCompetitiveCollection() throws Exception {
        try (Store store = new Store()) {
            CompetitiveEntry entry = new CompetitiveEntry(
                "puuid", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5, 99, 400, null, null, 1L);

            assertTrue(MongoDB.upsertCompetitive(entry));
            CompetitiveEntry loaded = MongoDB.findCompetitive("puuid", GameQueueType.RANKED_SOLO_5X5);

            assertEquals(List.of("competitive", "competitive"), store.collectionNames);
            assertEquals(400L, ((Number) store.saved.get("score")).longValue());
            assertEquals(99L, ((Number) store.saved.get("mmr")).longValue());
            assertEquals(400, loaded.score());
            assertTrue(!store.collectionNames.contains("summoner"));
            assertTrue(!store.collectionNames.contains("profile_statistics"));

            MongoDB.findLeaderboardPage(null, GameQueueType.RANKED_SOLO_5X5, LeagueConstants.GLOBAL_REGION, 0, 10);
            assertEquals(new Document("mmr", -1).append("score", -1), store.leaderboardSort);
            assertTrue(store.collectionNames.stream().allMatch("competitive"::equals));
        }
    }

    private static final class Store implements AutoCloseable {
        private final Field databaseField;
        private final Field readyField;
        private final Object originalDatabase;
        private final boolean originalReady;
        private final List<String> collectionNames = new ArrayList<>();
        private Document saved;
        private Document leaderboardSort;

        private Store() throws Exception {
            databaseField = MongoDB.class.getDeclaredField("database");
            databaseField.setAccessible(true);
            readyField = MongoDB.class.getDeclaredField("collectionsReady");
            readyField.setAccessible(true);
            originalDatabase = databaseField.get(null);
            originalReady = readyField.getBoolean(null);
            MongoCollection<Document> collection = proxy(MongoCollection.class, (object, method, args) -> switch (method.getName()) {
                case "replaceOne" -> {
                    assertTrue(((ReplaceOptions) args[2]).isUpsert());
                    saved = (Document) args[1];
                    yield UpdateResult.acknowledged(1, 1L, null);
                }
                case "find" -> proxy(FindIterable.class, (iterable, findMethod, findArgs) -> switch (findMethod.getName()) {
                    case "first" -> saved;
                    case "projection", "skip", "limit" -> iterable;
                    case "sort" -> {
                        Bson sort = (Bson) findArgs[0];
                        leaderboardSort = Document.parse(sort.toBsonDocument(Document.class,
                            MongoClientSettings.getDefaultCodecRegistry()).toJson());
                        yield iterable;
                    }
                    case "iterator" -> proxy(MongoCursor.class, (cursor, cursorMethod, cursorArgs) -> switch (cursorMethod.getName()) {
                        case "hasNext" -> false;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(cursorMethod.getName());
                    });
                    default -> throw new UnsupportedOperationException(findMethod.getName());
                });
                case "getNamespace" -> new MongoNamespace("test", "competitive");
                default -> throw new UnsupportedOperationException(method.getName());
            });
            MongoDatabase database = proxy(MongoDatabase.class, (object, method, args) -> {
                if (!method.getName().equals("getCollection")) throw new UnsupportedOperationException(method.getName());
                collectionNames.add((String) args[0]);
                return collection;
            });
            databaseField.set(null, database);
            readyField.setBoolean(null, true);
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
        }

        @Override
        public void close() throws Exception {
            databaseField.set(null, originalDatabase);
            readyField.setBoolean(null, originalReady);
        }
    }
}
