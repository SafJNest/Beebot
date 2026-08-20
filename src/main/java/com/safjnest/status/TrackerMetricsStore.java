package com.safjnest.status;

import com.safjnest.lol.model.status.TrackerMetrics;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.tracker.TrackerJobProgress;
import com.safjnest.lol.tracker.TrackerScheduler;

public final class TrackerMetricsStore {

    private TrackerMetricsStore() {}

    public static TrackerMetrics snapshot(long pendingGames) {
        TrackerScheduler.SchedulerStatus status = TrackerScheduler.status();
        boolean scheduled = status.scheduled();
        String scheduler = scheduled ? "running" : "stopped";
        long trackedSummoners = LeagueMetricsStore.trackedSummoners();

        return new TrackerMetrics(
            scheduler,
            TrackerJobProgress.trackingSnapshot(scheduled, status.trackingRunning(), status.nextTrackingAt(), trackedSummoners),
            TrackerJobProgress.highEloSnapshot(scheduled, status.highEloRunning(), status.nextHighEloAt()),
            TrackerJobProgress.gameAnalysisSnapshot(scheduled, status.gameQueueRunning(), status.nextGameQueueAt()),
            TrackerJobProgress.sampleGamesSnapshot(null),
            gamesSummary(status, pendingGames)
        );
    }

    // ============================================================================

    private static TrackerMetrics.GamesSummary gamesSummary(
        TrackerScheduler.SchedulerStatus status,
        long pendingGames
    ) {
        Long nextMatchLookupAt = status.nextMatchLookupAt() > 0 ? status.nextMatchLookupAt() : null;
        return new TrackerMetrics.GamesSummary(
            Math.max(0, pendingGames),
            Tracker.pendingMatchLookupCount(),
            nextMatchLookupAt
        );
    }
}
