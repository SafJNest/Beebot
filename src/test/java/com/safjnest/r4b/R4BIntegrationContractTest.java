package com.safjnest.r4b;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.R4B.api.dto.Build;
import com.R4B.api.dto.ChampionStatistics;
import com.R4B.api.dto.TransportJson;
import com.R4B.api.dto.match.Match;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.statistics.shared.ProfileLeafStats;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.api.dto.summoner.SummonerView;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.lol.CanonicalQueue;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LeagueShard;
import com.R4B.lol.TierDivisionType;
import com.R4B.lol.TeamType;
import com.safjnest.spring.config.LolApiConfig;
import com.safjnest.utils.JsonCodec;
import com.safjnest.spring.nosql.AbstractEntity;
import com.safjnest.spring.nosql.writer.SummonerWriter;
import com.safjnest.spring.nosql.writer.MatchWriter;
import com.safjnest.spring.nosql.MongoDB;
import com.safjnest.sql.QueryRecordParser;

public class R4BIntegrationContractTest {

    private static final String BASELINE_REVISION = "8a88645e5ecd9da75102f2ab5bc4eed6c3aaf5bd";
    private static final int BASELINE_CONTRACTS = 90;

    @Test
    public void preservesHttpJsonOfEveryCapturedResponseAndNestedComponent() throws Exception {
        ObjectMapper mapper = TransportJson.mapper();
        JsonNode baseline = baseline(mapper);
        assertEquals(BASELINE_CONTRACTS, baseline.size());
        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : baseline.properties()) {
            Class<?> type = Class.forName(entry.getKey());
            JsonNode actual = mapper.readTree(mapper.writeValueAsString(sample(type, new HashSet<>())));
            if (!entry.getValue().equals(actual)) differences.add(entry.getKey() + " expected="
                + entry.getValue() + " actual=" + actual);
        }
        assertTrue("HTTP differs from " + BASELINE_REVISION + ": " + String.join("; ", differences), differences.isEmpty());
    }

    @Test
    public void springUsesTheSamePublicTransportProfile() throws Exception {
        ObjectMapper transport = TransportJson.mapper();
        ObjectMapper spring = springMapper();
        for (Map.Entry<String, JsonNode> entry : baseline(transport).properties()) {
            Object value = sample(Class.forName(entry.getKey()), new HashSet<>());
            assertEquals(entry.getKey(), transport.valueToTree(value), spring.valueToTree(value));
        }
    }

    @Test
    public void keepsStorageFiltersSeparateFromPublicPayloads() throws Exception {
        ObjectMapper transport = TransportJson.mapper();
        for (Class<?> type : List.of(Build.class, ChampionStatistics.class)) {
            Object value = sample(type, new HashSet<>());
            JsonNode storage = transport.readTree(JsonCodec.toJson(value));
            JsonNode http = transport.valueToTree(value);
            assertTrue(type.getName(), storage.has("filter"));
            assertFalse(type.getName(), http.has("filter"));
        }
    }

    @Test
    public void roundTripsPopulatedHistoricalHttpPayloads() throws Exception {
        ObjectMapper mapper = TransportJson.mapper();
        JsonNode fixture = resource(mapper, "/r4b/populated-http-baseline.json");
        SummonerView profile = mapper.treeToValue(fixture.get("profile"), SummonerView.class);
        ProfileLeafStats leaf = mapper.treeToValue(fixture.get("profileLeaf"), ProfileLeafStats.class);
        Build.RuneOption rune = mapper.treeToValue(fixture.get("runeOption"), Build.RuneOption.class);

        assertEquals(fixture.get("profile"), mapper.readTree(mapper.writeValueAsString(profile)));
        assertEquals(fixture.get("profileLeaf"), mapper.readTree(mapper.writeValueAsString(leaf)));
        assertEquals(fixture.get("runeOption"), mapper.readTree(mapper.writeValueAsString(rune)));
        assertEquals(TierDivisionType.MASTER_I, profile.ranks().get(GameQueueType.RANKED_SOLO_5X5).tier());
        assertEquals("Name#TAG", profile.summoner().riotId());
        assertEquals(3, profile.overview().statistics().champions.get(412)
            .get(CanonicalQueue.RANKED_SOLO).get("UTILITY").games);
        assertEquals(Long.valueOf(15), leaf.spellDCasts.get(4));
        assertEquals(Long.valueOf(10), leaf.spellFCasts.get(14));
        assertEquals(List.of(9111, 9104, 8014), rune.configuration().primaryRunes());
        assertEquals(List.of(5008, 5008, 5002), rune.configuration().statShards());
        assertFalse(fixture.get("profile").get("summoner").has("userId"));
        assertFalse(fixture.get("profileLeaf").has("damageTaken"));
    }

    @Test
    public void readsHistoricalSdkCachesWithTheirNestedComponents() throws Exception {
        ObjectMapper mapper = TransportJson.mapper();
        JsonNode fixtures = resource(mapper, "/r4b/sdk-storage-baseline.json");
        assertEquals(46, fixtures.size());
        for (Map.Entry<String, JsonNode> entry : fixtures.properties()) {
            Class<?> type = Class.forName(entry.getKey());
            Object value = JsonCodec.fromJson(entry.getValue().toString(), type);
            assertTrue("Cannot decode historical SDK cache " + entry.getKey(), value != null);
            assertEquals(entry.getKey(), entry.getValue(), mapper.readTree(JsonCodec.toJson(value)));
        }
    }

    @Test
    public void preservesDirtyOperationsAndInMemoryStateAfterWriterExtraction() throws Exception {
        ObjectMapper mapper = TransportJson.mapper();
        JsonNode baseline = resource(mapper, "/r4b/writer-baseline.json");
        Map<GameQueueType, Rank> ranks = Map.of(GameQueueType.RANKED_FLEX_SR,
            new Rank(TierDivisionType.GOLD_II, 30, 12, 9));
        Summoner summoner = Summoner.hydrated("p42", "Name#TAG", LeagueShard.EUW1, 50, 123,
            "owner", true, ranks, List.of());
        SummonerWriter summonerWriter = SummonerWriter.existing(summoner);
        assertFalse(summonerWriter.isDirty());
        summonerWriter.setRank(GameQueueType.TEAM_BUILDER_RANKED_SOLO,
            new Rank(TierDivisionType.DIAMOND_II, 60, 10, 8)).setTracking("owner", true).unlink("owner");

        Match match = Match.hydrated();
        match.gameId = "EUW1_123";
        Participant participant = new Participant();
        participant.puuid = "p42";
        match.participants = List.of(participant);
        MatchWriter matchWriter = MatchWriter.existing(match);
        assertFalse(matchWriter.isDirty());
        matchWriter.setPatch(null).setParticipantField("p42", "rankProgress", Map.of("rank", "MASTER_I", "lp", 501));

        assertEquals(baseline.get("summonerPending"), mapper.readTree(JsonCodec.toJson(pending(summonerWriter))));
        assertEquals(baseline.get("matchPending"), mapper.readTree(JsonCodec.toJson(pending(matchWriter))));
        assertEquals(baseline.get("summonerUserId"), mapper.valueToTree(summoner.userId()));
        assertEquals(baseline.get("summonerTracking"), mapper.valueToTree(summoner.tracking()));
        assertEquals(baseline.get("summonerRanks"), mapper.readTree(JsonCodec.toJson(summoner.ranks())));
        assertTrue(summonerWriter.isDirty());
        assertTrue(matchWriter.isDirty());
        assertFalse(mapper.valueToTree(summoner).has("dirty"));
        assertFalse(mapper.valueToTree(match).has("dirty"));
    }

    private static Object pending(AbstractEntity<?> writer) throws Exception {
        Method method = AbstractEntity.class.getDeclaredMethod("pendingOperations", boolean.class);
        method.setAccessible(true);
        return method.invoke(writer, false);
    }

    @Test
    public void roundTripsMongoIdentityAndMatchThroughCanonicalReader() {
        Rank rank = new Rank(TierDivisionType.MASTER_I, 501, 25, 10);
        Summoner original = Summoner.hydrated("puuid-42", "Name#TAG", LeagueShard.EUW1, 500, 1234,
            "owner", true, Map.of(GameQueueType.RANKED_SOLO_5X5, rank), List.of(new Mastery(412, 7, 100000)));
        Summoner decoded = MongoDB.read(QueryRecordParser.fromDocument(MongoDB.toDocument(original)), Summoner.class);
        assertTrue(decoded != null);
        assertEquals(original, decoded);
        assertEquals("owner", decoded.userId());
        assertTrue(decoded.tracking());
        assertEquals(original.ranks(), decoded.ranks());
        assertEquals(original.masteries(), decoded.masteries());

        Match match = new Match();
        match.gameId = "EUW1_123";
        match.leagueShard = LeagueShard.EUW1;
        match.queue = GameQueueType.RANKED_SOLO_5X5;
        match.bans = Map.of(TeamType.BLUE, List.of(412), TeamType.RED, List.of(157));
        Participant participant = new Participant();
        participant.puuid = "puuid-42";
        participant.champion = 412;
        match.participants = List.of(participant);
        Match restored = MongoDB.read(QueryRecordParser.fromDocument(MongoDB.toDocument(match)), Match.class);
        assertTrue(restored != null);
        assertEquals("EUW1_123", restored.gameId);
        assertEquals(LeagueShard.EUW1, restored.leagueShard);
        assertEquals(GameQueueType.RANKED_SOLO_5X5, restored.queue);
        assertEquals(match.bans, restored.bans);
        assertEquals("puuid-42", restored.participants.getFirst().puuid);
        assertEquals(412, restored.participants.getFirst().champion);
    }

    private static JsonNode baseline(ObjectMapper mapper) throws Exception {
        return resource(mapper, "/r4b/http-baseline.json");
    }

    private static JsonNode resource(ObjectMapper mapper, String name) throws Exception {
        try (InputStream input = R4BIntegrationContractTest.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("Missing baseline fixture " + name);
            return mapper.readTree(input);
        }
    }

    private static Object sample(Class<?> type, Set<String> active) throws Exception {
        if (type == String.class) return "sample";
        if (type == int.class || type == Integer.class) return 7;
        if (type == long.class || type == Long.class) return 11L;
        if (type == double.class || type == Double.class) return 0.5d;
        if (type == float.class || type == Float.class) return 0.5f;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == byte.class || type == Byte.class) return (byte) 1;
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (List.class.isAssignableFrom(type)) return type.isInterface() ? List.of() : type.getDeclaredConstructor().newInstance();
        if (Set.class.isAssignableFrom(type)) return type.isInterface() ? Set.of() : type.getDeclaredConstructor().newInstance();
        if (Map.class.isAssignableFrom(type)) return type.isInterface() ? Map.of() : type.getDeclaredConstructor().newInstance();
        if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
        if (type.getSimpleName().equals("Filter") || type.getSimpleName().equals("ActivityFilter")) return null;
        if (!(type.getName().startsWith("com.R4B.") || type.getName().startsWith("com.hive.status."))) return null;
        if (!active.add(type.getName())) return null;
        try {
            Constructor<?> constructor = null;
            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] parameterTypes = new Class<?>[components.length];
                for (int index = 0; index < components.length; index++) parameterTypes[index] = components[index].getType();
                constructor = type.getDeclaredConstructor(parameterTypes);
            } else {
                Constructor<?>[] constructors = type.getDeclaredConstructors();
                Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));
                if (constructors.length != 0) constructor = constructors[0];
            }
            if (constructor == null) return null;
            constructor.setAccessible(true);
            Object[] parameters = new Object[constructor.getParameterCount()];
            for (int index = 0; index < parameters.length; index++) parameters[index] = sample(constructor.getParameterTypes()[index], active);
            Object object = constructor.newInstance(parameters);
            if (!type.isRecord()) for (Field field : type.getFields()) {
                if (!Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) field.set(object, sample(field.getType(), active));
            }
            return object;
        } finally {
            active.remove(type.getName());
        }
    }

    private static ObjectMapper springMapper() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new LolApiConfig().configureMessageConverters(converters);
        return ((MappingJackson2HttpMessageConverter) converters.getFirst()).getObjectMapper();
    }
}
