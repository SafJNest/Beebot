package com.safjnest.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.safjnest.lol.model.status.TrackerMetrics;

public class TrackerMetricsStoreTest {

    @Test
    public void snapshotReturnsTrackerSections() {
        TrackerMetrics metrics = TrackerMetricsStore.snapshot(7);

        assertNotNull(metrics);
        assertNotNull(metrics.tracking());
        assertNotNull(metrics.highElo());
        assertNotNull(metrics.gameAnalysis());
        assertNotNull(metrics.sampleGames());
        assertNotNull(metrics.games());
        assertEquals(7, metrics.games().pendingGames());
        assertEquals("stopped", metrics.scheduler());
    }
}
