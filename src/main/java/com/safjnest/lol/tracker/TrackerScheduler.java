package com.safjnest.lol.tracker;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.service.LeaderboardService;
import com.safjnest.lol.tracker.TrackerState.Priority;
import com.safjnest.utils.TimeConstant;

import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

public class TrackerScheduler {

    private static final long TRACKING_PERIOD_MS = TimeConstant.MINUTE * 10;
    private static final long HIGH_ELO_INITIAL_DELAY_MS = TimeConstant.HOUR;
    private static final long HIGH_ELO_PERIOD_MS = TimeConstant.HOUR;
    private static volatile boolean started;
    private static volatile long startedAt;
    private static volatile boolean trackingRunning;
    private static volatile boolean highEloRunning;
    private static volatile boolean gameQueueRunning;

    public static synchronized void start() {
        if (started) return;
        started = true;
        startedAt = System.currentTimeMillis();

        if (App.isTesting()) return;

        ChronoTask track = () -> retrieveSummoners();
        track.scheduleAtFixedRate(0, TRACKING_PERIOD_MS, TimeUnit.MILLISECONDS);

        ChronoTask trackQueuedGames = () -> popSet();
        trackQueuedGames.scheduleAtFixedTime(0, 0, 0);

        //ChronoTask trackSampleGames = () -> retrieveSampleGames();
        //trackSampleGames.scheduleAtFixedTime(2, 0, 0);

        ChronoTask retrieveHighEloEntries = () -> retrieveHighEloEntries();
        retrieveHighEloEntries.scheduleAtFixedRate(HIGH_ELO_INITIAL_DELAY_MS, HIGH_ELO_PERIOD_MS, TimeUnit.MILLISECONDS);

        ChronoTask refreshMatchLookups = () -> Tracker.processMatchLookups();
        refreshMatchLookups.scheduleAtFixedRate(0, TimeConstant.SECOND * 10, TimeUnit.MILLISECONDS);

        ChronoTask refreshChampionData = () -> refreshChampionData();
        refreshChampionData.scheduleAtFixedTime(3, 0, 0);

        ChronoTask clearTimelineCache = () -> DataCall.getCacheProvider().clear(URLEndpoint.V5_TIMELINE, new LinkedHashMap<>());
        clearTimelineCache.scheduleAtFixedRate(TimeConstant.HOUR * 12, TimeConstant.HOUR * 12, TimeUnit.MILLISECONDS);

        ChronoTask rebuildLeaderboard = LeaderboardService::rebuild;
        rebuildLeaderboard.scheduleAtFixedRate(0, TimeConstant.HOUR * 12, TimeUnit.MILLISECONDS);
    }

    public static void retrieveSummoners() {
        trackingRunning = true;
        try {
            TrackerState.acquire(Priority.HIGH);
            try { Tracker.retrieveSummoners(); }
            finally { TrackerState.release(Priority.HIGH); }
        } finally {
            trackingRunning = false;
        }
    }

    public static void popSet() {
        gameQueueRunning = true;
        try {
            TrackerState.awaitCondition(Priority.MID);

            Set<LOLMatch> toAnalyze = Tracker.popQueue();
            if (toAnalyze.isEmpty()) return;

            TrackerState.acquire(Priority.MID);
            try {
                for (LOLMatch match : toAnalyze) {
                    TrackerState.awaitCondition(Priority.MID);
                    try {
                        Tracker.analyzeMatchHistory(match).completeWithException();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                TrackerState.release(Priority.MID);
            }
        } finally {
            gameQueueRunning = false;
        }
    }

    public static void retrieveSampleGames(GameQueueType queue) {
        TrackerState.awaitCondition(Priority.LOW);
        Tracker.retrieveSampleGames(queue);
    }

    public static void retrieveHighEloEntries() {
        highEloRunning = true;
        try {
            TrackerState.awaitCondition(Priority.MID);
            Tracker.retrieveHighEloEntries();
        } finally {
            highEloRunning = false;
        }
    }

    public static void refreshChampionData() {
        DatabaseTracker.enqueueChampionDataRefresh();
    }

    public static void retrieveAllEntries() {
        TrackerState.awaitCondition(Priority.LOW);
        Tracker.retrieveAllEntries();
    }

    public static SchedulerStatus status() {
        long now = System.currentTimeMillis();
        boolean scheduled = started && !App.isTesting();
        return new SchedulerStatus(
            scheduled,
            trackingRunning,
            highEloRunning,
            gameQueueRunning,
            scheduled ? nextPeriodicRun(0, TRACKING_PERIOD_MS, now) : 0,
            scheduled ? nextPeriodicRun(HIGH_ELO_INITIAL_DELAY_MS, HIGH_ELO_PERIOD_MS, now) : 0,
            scheduled ? nextMidnight(now) : 0
        );
    }

    private static long nextPeriodicRun(long initialDelay, long period, long now) {
        long first = startedAt + initialDelay;
        if (now < first) return first;
        return first + ((now - first) / period + 1) * period;
    }

    private static long nextMidnight(long now) {
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= now) next.add(Calendar.DAY_OF_MONTH, 1);
        return next.getTimeInMillis();
    }

    public record SchedulerStatus(
        boolean scheduled,
        boolean trackingRunning,
        boolean highEloRunning,
        boolean gameQueueRunning,
        long nextTrackingAt,
        long nextHighEloAt,
        long nextGameQueueAt
    ) {}
}
