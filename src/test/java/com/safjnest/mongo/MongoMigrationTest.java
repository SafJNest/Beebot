package com.safjnest.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MongoMigrationTest {

    @Test
    public void migrationOptionsAreBatchableAndResumable() {
        MongoMigration.Options options = new MongoMigration.Options(true, 50, "run-1", true, 100);

        assertEquals(50, options.batchSize());
        assertEquals("run-1", options.runId());
        assertEquals(100, options.highWaterMark());
    }

    @Test
    public void invalidBatchIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MongoMigration.Options(false, 0, "run", false, 0));
    }
}
