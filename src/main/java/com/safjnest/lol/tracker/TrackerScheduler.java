package com.safjnest.lol.tracker;

import java.util.concurrent.TimeUnit;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.service.LeaderboardService;
import com.safjnest.lol.service.RankService;
import com.safjnest.utils.TimeConstant;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class TrackerScheduler {

    private static volatile boolean started;
    private static volatile boolean cronScheduled;

    private TrackerScheduler() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        scheduleIfEnabled();
    }

    public static synchronized void scheduleIfEnabled() {
        if (cronScheduled || !App.tracking()) return;
        cronScheduled = true;

        ChronoTask track = TrackerScheduler::retrieveSummoners;
        track.scheduleAtFixedRate(0, TimeConstant.MINUTE * 10, TimeUnit.MILLISECONDS);

        ChronoTask retrieveHighEloEntries = TrackerScheduler::retrieveHighEloEntries;
        retrieveHighEloEntries.scheduleAtFixedRate(TimeConstant.HOUR, TimeConstant.HOUR, TimeUnit.MILLISECONDS);

        ChronoTask refreshChampionData = TrackerScheduler::refreshChampionData;
        refreshChampionData.scheduleAtFixedTime(3, 0, 0);

        ChronoTask rebuildLeaderboard = LeaderboardService::rebuild;
        rebuildLeaderboard.scheduleAtFixedRate(0, TimeConstant.HOUR * 12, TimeUnit.MILLISECONDS);
    }

    public static void retrieveSummoners() {
        Tracker.retrieveSummoners();
    }

    public static void retrieveSampleGames(GameQueueType queue) {
        Tracker.retrieveSampleGames(queue);
    }

    public static void retrieveHighEloEntries() {
        RankService.enqueueRankEntries(true, false);
    }

    public static void retrieveAllEntries() {
        RankService.enqueueRankEntries(false, true);
    }

    public static void refreshChampionData() {
        ComputeScheduler.enqueueChampionDataRefresh();
    }
}
