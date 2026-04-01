package com.safjnest.lol.tracker;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.safjnest.App;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.tracker.TrackerState.Priority;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
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

        Set<LOLMatch> toAnalyze;
        synchronized (Tracker.matchQueue) {
            if (Tracker.matchQueue.isEmpty()) return;
            toAnalyze = new HashSet<>(Tracker.matchQueue);
            Tracker.matchQueue.clear();
        }

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

    public static void retriveSampleGames() {
        TrackerState.awaitCondition(Priority.LOW);
        Tracker.retriveSampleGames();
    }

    public static void retriveHighEloEntries() {
        TrackerState.awaitCondition(Priority.MID);
        Tracker.retriveHighEloEntries();
    }
}