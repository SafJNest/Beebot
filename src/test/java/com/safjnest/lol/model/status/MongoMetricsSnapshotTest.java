package com.safjnest.lol.model.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.safjnest.utils.JsonCodec;

public class MongoMetricsSnapshotTest {

    @Test
    public void serializesMongoBlockOnBotStatus() {
        MongoMetrics mongo = new MongoMetrics(
                new MongoOperationsMetrics(
                        1,
                        new MongoOperationRates(0, 12.4, 1.1, 0, 0.3, 0.8, 14.6),
                        java.util.List.of(new MongoOperationSample(1_755_680_400_000L,
                                new MongoOperationRates(0, 12.4, 1.1, 0, 0.3, 0.8, 14.6)))),
                new MongoPerformanceMetrics(
                        new MongoHottestCollection("match", 82, 82),
                        new MongoHottestCollection("match", 41.5, 415),
                        10,
                        300,
                        java.util.Map.of("find", 12.3),
                        java.util.List.of(new MongoCollectionPerformance("match", 120, 45.0, 890)),
                        java.util.List.of(new MongoSlowOperation(
                                "aggregate",
                                "match",
                                890,
                                1_755_680_400_123L,
                                java.util.Map.of("aggregate", "beebot.match", "pipeline", java.util.List.of())))),
                5L,
                new MongoMemoryMetrics(259L, 3710L));

        BotStatus status = BotStatus.online(
                new LeagueMetrics(1, 2, 3, java.util.Map.of()),
                java.util.List.of(),
                null,
                null,
                null,
                mongo);

        String json = JsonCodec.toJson(status);
        assertNotNull(json);
        assertTrueContains(json, "\"mongo\"");
        assertTrueContains(json, "\"hottestNow\"");
        assertTrueContains(json, "\"query\"");
    }

    private static void assertTrueContains(String json, String token) {
        assertEquals(true, json.contains(token));
    }
}
