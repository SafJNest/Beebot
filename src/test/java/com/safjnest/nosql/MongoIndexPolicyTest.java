package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.junit.Assume;
import org.junit.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoIndexPolicyTest {

    private static final String TEST_DATABASE = "beebot_index_policy_test";

    @Test
    public void registryContainsTheDeclaredPolicy() throws Exception {
        Field field = MongoDB.class.getDeclaredField("INDEX_DEFINITIONS");
        field.setAccessible(true);
        List<?> definitions = (List<?>) field.get(null);

        assertEquals(20, definitions.size());
        Set<String> names = new HashSet<>();
        for (Object definition : definitions) {
            Method name = definition.getClass().getDeclaredMethod("name");
            name.setAccessible(true);
            names.add((String) name.invoke(definition));
        }

        assertEquals(Set.of(
                "summoner_search_prefix", "summoner_riot_id", "summoner_user_accounts", "summoner_tracking_true",
                "summoner_leaderboard_region", "summoner_leaderboard_global", "match_participant_time",
                "match_shard_time", "match_shard_patch_time", "match_patch", "match_champion_filter",
                "match_champion_keyset", "profile_statistics_identity", "profile_statistics_period",
                "profile_activity_identity", "profile_matchups_identity",
                "champion_builds_filter", "champion_stats_filter_champion", "champions_indexable_patch",
                "profiles_indexable_order"), names);

        assertPolicy(definitions, "summoner_search_prefix", "summoner",
                new Document("region", 1).append("riotSearch", 1).append("riotId", 1), false, null);
        assertPolicy(definitions, "summoner_riot_id", "summoner",
                new Document("region", 1).append("riotId", 1), false, null);
        assertPolicy(definitions, "summoner_user_accounts", "summoner",
                new Document("userId", 1).append("_id", 1), false, null);
        assertPolicy(definitions, "summoner_tracking_true", "summoner",
                new Document("tracking", 1), false, new Document("tracking", true));
        assertPolicy(definitions, "summoner_leaderboard_region", "summoner",
                new Document("region", 1).append("ranks.queue", 1).append("ranks.rank", 1), false, null);
        assertPolicy(definitions, "summoner_leaderboard_global", "summoner",
                new Document("ranks.queue", 1).append("ranks.rank", 1).append("region", 1), false, null);
        assertPolicy(definitions, "match_participant_time", "match",
                new Document("participants.puuid", 1).append("timeStart", 1).append("_id", 1), false, null);
        assertPolicy(definitions, "match_shard_time", "match",
                new Document("region", 1).append("timeStart", -1), false, null);
        assertPolicy(definitions, "match_shard_patch_time", "match",
                new Document("region", 1).append("patchMajor", 1).append("timeStart", -1), false, null);
        assertPolicy(definitions, "match_patch", "match", new Document("patchMajor", 1), false, null);
        assertPolicy(definitions, "match_champion_filter", "match",
                new Document("queue", 1).append("region", 1).append("rank", 1)
                        .append("participants.champion", 1).append("participants.lane", 1).append("patchMajor", 1), false, null);
        assertPolicy(definitions, "match_champion_keyset", "match",
                new Document("queue", 1).append("region", 1).append("rank", 1)
                        .append("participants.champion", 1).append("participants.lane", 1), false, null);
        assertPolicy(definitions, "profile_statistics_identity", "profile_statistics",
                new Document("puuid", 1).append("filterKey", 1), true, null);
        assertPolicy(definitions, "profile_statistics_period", "profile_statistics",
                new Document("puuid", 1).append("timeEnd", -1).append("timeStart", 1), false, null);
        assertPolicy(definitions, "profile_activity_identity", "profile_activity",
                new Document("puuid", 1).append("filterKey", 1), true, null);
        assertPolicy(definitions, "profile_matchups_identity", "profile_matchups",
                new Document("puuid", 1).append("filterKey", 1), true, null);
        assertPolicy(definitions, "champion_builds_filter", "champion_builds",
                new Document("filterKey", 1), false, null);
        assertPolicy(definitions, "champion_stats_filter_champion", "champion_stats",
                new Document("filterKey", 1).append("championId", 1), false, null);
        assertPolicy(definitions, "champions_indexable_patch", "champions_indexable",
                new Document("patchMajor", 1).append("championId", 1).append("role", 1), false, null);
        assertPolicy(definitions, "profiles_indexable_order", "profiles_indexable",
                new Document("region", 1).append("riotId", 1), false, null);
    }

    @Test
    public void bootstrapIsIdempotentAndCreatesTheDeclaredIndexes() throws Exception {
        String uri = testUri();
        Assume.assumeTrue(uri != null);
        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase database = client.getDatabase(TEST_DATABASE);
            database.drop();
            invoke(database, "ensureCollections");
            invoke(database, "ensureIndexes");

            List<Document> first = database.getCollection("summoner").listIndexes().into(new java.util.ArrayList<>());
            assertTrue(database.listCollectionNames().into(new java.util.ArrayList<>()).contains("champions_indexable"));
            assertTrue(database.listCollectionNames().into(new java.util.ArrayList<>()).contains("profiles_indexable"));
            invoke(database, "ensureIndexes");
            List<Document> second = database.getCollection("summoner").listIndexes().into(new java.util.ArrayList<>());

            assertEquals(first.size(), second.size());
            Set<String> names = new HashSet<>();
            for (Document index : second) names.add(index.getString("name"));
            assertTrue(names.contains("summoner_search_prefix"));
            assertTrue(names.contains("summoner_leaderboard_region"));
            assertTrue(names.contains("summoner_leaderboard_global"));

            Document identity = findIndex(
                    database.getCollection("profile_statistics").listIndexes().into(new java.util.ArrayList<>()),
                    "profile_statistics_identity");
            assertTrue(identity.getBoolean("unique", false));
        } finally {
            try (MongoClient cleanup = MongoClients.create(uri)) {
                cleanup.getDatabase(TEST_DATABASE).drop();
            }
        }
    }

    @Test
    public void duplicateProfileStatisticsBlockUniqueIndexCreation() throws Exception {
        String uri = testUri();
        Assume.assumeTrue(uri != null);
        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase database = client.getDatabase(TEST_DATABASE);
            database.drop();
            invoke(database, "ensureCollections");
            database.getCollection("profile_statistics").insertMany(List.of(
                    new Document("puuid", "duplicate-puuid").append("filterKey", "duplicate-filter"),
                    new Document("puuid", "duplicate-puuid").append("filterKey", "duplicate-filter")));

            try {
                invoke(database, "ensureIndexes");
                fail("Expected duplicate profile statistics to block the unique index");
            } catch (IllegalStateException exception) {
                assertTrue(exception.getMessage().contains("profile_statistics_identity"));
            }
        } finally {
            try (MongoClient cleanup = MongoClients.create(uri)) {
                cleanup.getDatabase(TEST_DATABASE).drop();
            }
        }
    }

    private static String testUri() {
        String uri = System.getenv("MONGO_TEST_URI");
        return uri == null || uri.isBlank() ? null : uri;
    }

    private static void invoke(MongoDatabase database, String methodName) throws Exception {
        Method method = MongoDB.class.getDeclaredMethod(methodName, MongoDatabase.class);
        method.setAccessible(true);
        try {
            method.invoke(null, database);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw exception;
        }
    }

    private static void assertPolicy(
            List<?> definitions,
            String name,
            String collection,
            Document keys,
            boolean unique,
            Document partialFilter) throws Exception {
        Object definition = definitionByName(definitions, name);
        assertEquals(collection, property(definition, "collection"));
        assertEquals(keys, property(definition, "keys"));
        assertEquals(Boolean.valueOf(unique), property(definition, "unique"));
        assertEquals(partialFilter, property(definition, "partialFilter"));
    }

    private static Object definitionByName(List<?> definitions, String expectedName) throws Exception {
        for (Object definition : definitions) {
            if (expectedName.equals(property(definition, "name"))) return definition;
        }
        fail("Missing registry definition " + expectedName);
        return null;
    }

    private static Object property(Object definition, String property) throws Exception {
        Method method = definition.getClass().getDeclaredMethod(property);
        method.setAccessible(true);
        return method.invoke(definition);
    }

    private static Document findIndex(List<Document> indexes, String name) {
        for (Document index : indexes) if (name.equals(index.getString("name"))) return index;
        fail("Missing index " + name);
        return null;
    }
}
