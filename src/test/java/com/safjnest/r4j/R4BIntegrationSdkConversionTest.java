package com.safjnest.r4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.utils.JsonCodec;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.lol.LeagueShard;

public class R4BIntegrationSdkConversionTest {

    @Test
    public void convertsEveryPopulatedSdkComponentInBothDirectionsWithoutRuntimeInitialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mapping = resource(mapper, "/r4b/sdk-type-mapping.json");
        JsonNode fixtures = resource(mapper, "/r4b/sdk-storage-baseline.json");
        assertEquals(46, fixtures.size());
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, JsonNode> fixture : fixtures.properties()) {
            try {
                Class<?> sdkClass = Class.forName(mapping.get(fixture.getKey()).asText());
                Class<?> sharedClass = Class.forName(fixture.getKey());
                Object original = JsonCodec.fromJson(fixture.getValue().toString(), sdkClass);
                assertNotNull(sdkClass.getName(), original);
                Object shared = RiotGateway.data(original, sharedClass);
                assertNotNull(sharedClass.getName(), shared);
                assertEquals(sharedClass.getName(), fixture.getValue(), mapper.readTree(JsonCodec.toJson(shared)));
                Object restored = RiotGateway.data(shared, sdkClass);
                assertEquals(sdkClass.getName(), fixture.getValue(), mapper.readTree(JsonCodec.toJson(restored)));
            } catch (Exception | AssertionError failure) {
                failures.add(fixture.getKey() + ": " + failure.getMessage());
            }
        }
        assertTrue(String.join("; ", failures), failures.isEmpty());
    }

    @Test
    public void decodesHistoricalRawIdentityCachesToCanonicalModels() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode fixtures = resource(mapper, "/r4b/identity-sdk-storage-baseline.json");
        String summonerJson = fixtures.get("no.stelar7.api.r4j.pojo.lol.summoner.Summoner").toString();
        String masteryJson = "[" + fixtures.get("no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery") + "]";
        Summoner summoner = RiotGateway.summonerFromCache(summonerJson);
        List<Mastery> masteries = RiotGateway.masteriesFromCache(masteryJson);

        assertNotNull(summoner);
        assertEquals("puuid-42", summoner.puuid());
        assertEquals("Name", summoner.riotId());
        assertEquals(LeagueShard.EUW1, summoner.region());
        assertEquals(500, summoner.level());
        assertEquals(1234, summoner.icon());
        assertEquals(List.of(new Mastery(412, 7, 100000)), masteries);
        assertEquals(null, RiotGateway.summonerFromCache("{invalid"));
        assertEquals(null, RiotGateway.masteriesFromCache("{invalid"));
    }

    private static JsonNode resource(ObjectMapper mapper, String name) throws Exception {
        try (InputStream input = R4BIntegrationSdkConversionTest.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("Missing SDK fixture " + name);
            return mapper.readTree(input);
        }
    }
}
