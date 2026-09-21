package com.safjnest.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.safjnest.lol.model.status.LeagueMetrics;

public class LeagueMetricsStoreTest {

    @Test
    public void snapshotReturnsNonNullRankTotalsMap() {
        LeagueMetrics metrics = LeagueMetricsStore.snapshot();
        assertNotNull(metrics.ranksByQueue());
    }

    @Test
    public void snapshotNeverReturnsNegativeCounters() {
        LeagueMetrics metrics = LeagueMetricsStore.snapshot();
        assertEquals(true, metrics.gamesAnalyzed() >= 0);
        assertEquals(true, metrics.totalSummoners() >= 0);
        assertEquals(true, metrics.totalMasteries() >= 0);
    }
}
