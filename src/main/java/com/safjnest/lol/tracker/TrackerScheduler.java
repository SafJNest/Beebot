package com.safjnest.lol.tracker;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.service.ChampionDataRefreshService;
import com.safjnest.lol.service.LeaderboardService;
import com.safjnest.lol.tracker.TrackerState.Priority;
import com.safjnest.utils.TimeConstant;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

public class TrackerScheduler {

    private static final ChampionDataRefreshService championDataRefreshService = new ChampionDataRefreshService();
    private static boolean started;

    public static synchronized void start() {
        if (started) return;
        started = true;

        if (App.isTesting()) return;

        ChronoTask track = () -> retrieveSummoners();
        track.scheduleAtFixedRate(0, TimeConstant.MINUTE * 10, TimeUnit.MILLISECONDS);

        ChronoTask trackQueuedGames = () -> popSet();
        trackQueuedGames.scheduleAtFixedTime(0, 0, 0);

        //ChronoTask trackSampleGames = () -> retrieveSampleGames();
        //trackSampleGames.scheduleAtFixedTime(2, 0, 0);

        ChronoTask retrieveHighEloEntries = () -> retrieveHighEloEntries();
        retrieveHighEloEntries.scheduleAtFixedRate(0, TimeConstant.HOUR, TimeUnit.MILLISECONDS);

        ChronoTask refreshMatchLookups = () -> Tracker.processMatchLookups();
        refreshMatchLookups.scheduleAtFixedRate(0, TimeConstant.SECOND * 10, TimeUnit.MILLISECONDS);

        ChronoTask refreshLeaderboardDistribution = () -> LeaderboardService.rebuildDistribution();
        refreshLeaderboardDistribution.scheduleAtFixedRate(0, TimeConstant.DAY, TimeUnit.MILLISECONDS);

        ChronoTask refreshChampionData = () -> refreshChampionData();
        refreshChampionData.scheduleAtFixedTime(3, 0, 0);

        ChronoTask clearTimelineCache = () -> DataCall.getCacheProvider().clear(URLEndpoint.V5_TIMELINE, new LinkedHashMap<>());
        clearTimelineCache.scheduleAtFixedRate(TimeConstant.HOUR * 12, TimeConstant.HOUR * 12, TimeUnit.MILLISECONDS);
    }

    public static void retrieveSummoners() {
        TrackerState.acquire(Priority.HIGH);
        try { Tracker.retrieveSummoners(); }
        finally { TrackerState.release(Priority.HIGH); }
    }

    public static void popSet() {
        TrackerState.awaitCondition(Priority.MID);

        Set<LOLMatch> toAnalyze = Tracker.popQueue();
        if (toAnalyze.isEmpty()) return;

        TrackerState.acquire(Priority.MID);
        try {
            BotLogger.info("[LPTracker] Analyzing " + toAnalyze.size() + " queued matches");
            int i = 0;
            for (LOLMatch match : toAnalyze) {
                TrackerState.awaitCondition(Priority.MID);
                try {
                    Tracker.analyzeMatchHistory(match).completeWithException();
                    BotLogger.info("[LPTracker] [" + i + "/" + toAnalyze.size() + "] Pushed match " + match.getGameId() + " (" + match.getPlatform() + " - " + match.getQueue() + ")");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                i++;
            }
        } finally {
            TrackerState.release(Priority.MID);
        }
    }

    public static void retrieveSampleGames(GameQueueType queue) {
        TrackerState.awaitCondition(Priority.LOW);
        Tracker.retrieveSampleGames(queue);
    }

    public static void retrieveHighEloEntries() {
        TrackerState.awaitCondition(Priority.MID);
        Tracker.retrieveHighEloEntries();
    }

    public static void refreshChampionData() {
        championDataRefreshService.refresh();
    }

    public static void retrieveAllEntries() {
        TrackerState.awaitCondition(Priority.LOW);
        Tracker.retrieveAllEntries();
    }
}
