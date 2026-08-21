package com.safjnest.nosql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Before;
import org.junit.Test;

import com.safjnest.lol.model.status.MongoPerformanceMetrics;

public class MongoCommandMonitorTest {

    @Before
    public void resetMonitor() {
        MongoCommandMonitor.resetForTest();
    }

    @Test
    public void mapsWireCommandsToAtlasCategories() {
        assertEquals("query", MongoCommandMonitor.atlasCategory("find"));
        assertEquals("query", MongoCommandMonitor.atlasCategory("countDocuments"));
        assertEquals("insert", MongoCommandMonitor.atlasCategory("insertMany"));
        assertEquals("update", MongoCommandMonitor.atlasCategory("updateMany"));
        assertEquals("delete", MongoCommandMonitor.atlasCategory("deleteMany"));
        assertEquals("getmore", MongoCommandMonitor.atlasCategory("getMore"));
        assertEquals("command", MongoCommandMonitor.atlasCategory("aggregate"));
    }

    @Test
    public void ignoresInternalHandshakeCommands() {
        assertTrue(MongoCommandMonitor.ignored("hello"));
        assertTrue(MongoCommandMonitor.ignored("isMaster"));
        assertTrue(MongoCommandMonitor.ignored("ping"));
    }

    @Test
    public void extractsCollectionFromCommandDocument() {
        BsonDocument command = new BsonDocument("find", new BsonString("beebot.match"));
        assertEquals("match", MongoCommandMonitor.extractCollection("find", command));
    }

    @Test
    public void tracksHottestNowAndRecentAcrossBuckets() {
        MongoCommandMonitor.recordOperationForTest("find", "match", 50);
        MongoCommandMonitor.recordOperationForTest("find", "match", 40);
        MongoCommandMonitor.tickSecond();

        MongoPerformanceMetrics first = MongoCommandMonitor.snapshotPerformance();
        assertEquals("match", first.hottestNow().name());
        assertEquals(2, first.hottestNow().ops());
        assertEquals(2.0, first.hottestNow().opsPerSecond(), 0.0001);

        for (int index = 0; index < 3; index++) {
            MongoCommandMonitor.tickSecond();
        }

        MongoPerformanceMetrics stillRecent = MongoCommandMonitor.snapshotPerformance();
        assertNull(stillRecent.hottestNow());
        assertEquals("match", stillRecent.hottestRecent().name());

        for (int index = 0; index < MongoCommandMonitor.RECENT_WINDOW_SECONDS; index++) {
            MongoCommandMonitor.tickSecond();
        }

        MongoPerformanceMetrics afterWindow = MongoCommandMonitor.snapshotPerformance();
        assertNull(afterWindow.hottestNow());
        assertNull(afterWindow.hottestRecent());
    }

    @Test
    public void exposesQueryPayloadOnSlowest() {
        Map<String, Object> query = Map.of(
                "find", "beebot.match",
                "filter", Map.of("region", "EUW1"));
        MongoCommandMonitor.recordOperationForTest("find", "match", 890, query);

        MongoPerformanceMetrics metrics = MongoCommandMonitor.snapshotPerformance();
        assertEquals(1, metrics.slowest().size());
        assertEquals("find", metrics.slowest().get(0).command());
        assertEquals("match", metrics.slowest().get(0).collection());
        assertEquals("beebot.match", metrics.slowest().get(0).query().get("find"));
    }

    @Test
    public void expiresSlowestAfterRecentWindow() {
        MongoCommandMonitor.recordOperationForTest("aggregate", "match", 890);
        assertEquals(1, MongoCommandMonitor.snapshotPerformance().slowest().size());

        MongoCommandMonitor.ageOperationForTest(0, 11_000L);

        MongoPerformanceMetrics expired = MongoCommandMonitor.snapshotPerformance();
        assertTrue(expired.slowest().isEmpty());
    }
}
