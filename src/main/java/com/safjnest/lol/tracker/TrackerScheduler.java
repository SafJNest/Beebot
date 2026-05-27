package com.safjnest.lol.tracker;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.service.ChampionDataRefreshService;
import com.safjnest.lol.tracker.TrackerState.Priority;
import com.safjnest.utils.TimeConstant;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

public class TrackerScheduler {

    static {
        if (!App.isTesting()) {
            ChronoTask track = () -> retriveSummoners();
            track.scheduleAtFixedRate(0, TimeConstant.MINUTE * 10, TimeUnit.MILLISECONDS);

            ChronoTask trackQueuedGames = () -> popSet();
            trackQueuedGames.scheduleAtFixedTime(0, 0, 0);

            //ChronoTask trackSampleGames = () -> retriveSampleGames();
            //trackSampleGames.scheduleAtFixedTime(2, 0, 0);

            ChronoTask retriveHighEloEntries = () -> retriveHighEloEntries();
            retriveHighEloEntries.scheduleAtFixedRate(0, TimeConstant.HOUR, TimeUnit.MILLISECONDS);

            ChronoTask refreshChampionData = () -> refreshChampionData();
            refreshChampionData.scheduleAtFixedTime(3, 0, 0);

            ChronoTask clearTimelineCache = () -> DataCall.getCacheProvider().clear(URLEndpoint.V5_TIMELINE, new LinkedHashMap<>());
            clearTimelineCache.scheduleAtFixedRate(TimeConstant.HOUR * 12, TimeConstant.HOUR * 12, TimeUnit.MILLISECONDS);
        }
    }

    public static void retriveSummoners() {
        TrackerState.acquire(Priority.HIGH);
        try { Tracker.retriveSummoners(); } 
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

    public static void retriveSampleGames(GameQueueType queue) {
        TrackerState.awaitCondition(Priority.LOW);
        Tracker.retriveSampleGames(queue);
    }

    public static void retriveHighEloEntries() {
        TrackerState.awaitCondition(Priority.MID);
        Tracker.retriveHighEloEntries();
    }

    public static void refreshChampionData() {
        TrackerState.awaitCondition(Priority.LOW);
        TrackerState.acquire(Priority.LOW);
        try { new ChampionDataRefreshService().refresh(); }
        finally { TrackerState.release(Priority.LOW); }
    }
}
