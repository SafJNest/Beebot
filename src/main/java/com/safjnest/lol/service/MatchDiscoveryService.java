package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.SyncScheduler;
import com.safjnest.lol.utils.MatchUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public final class MatchDiscoveryService {

    private static final int MATCH_LOOKUP_MAX_RETRIES = 3;

    private MatchDiscoveryService() {}

    public static void enqueueLookup(LeagueShard shard, String gameId) {
        if (shard == null || gameId == null || gameId.isBlank()) return;
        QueueHandler.background(SyncScheduler.class, shard, "match-lookup:" + shard.name() + ':' + gameId,
            "match lookup id=" + gameId, task -> lookup(task, shard, gameId));
    }

    public static void enqueueRecentMatches(Summoner summoner, int limit) {
        if (summoner == null || summoner.getPUUID() == null || summoner.getPlatform() == null || limit < 1) return;
        String key = "recent-matches:" + summoner.getPlatform().name() + ':' + summoner.getPUUID();
        QueueHandler.background(SyncScheduler.class, summoner.getPlatform(), key,
            "recent matches puuid=" + summoner.getPUUID(), task -> {
                QueueHandler.retain(task);
                MatchService.getIdsAsync(summoner, null, 0, limit, 0, null).whenComplete((matchIds, failure) ->
                    QueueHandler.resume(task, () -> enqueueMissingMatches(summoner.getPlatform(), matchIds, failure)));
                return null;
            });
    }

    public static void importHistory(Summoner summoner, GameQueueType queue) {
        if (summoner == null || summoner.getPlatform() == null) return;
        try {
            List<String> matchIds = new ArrayList<>();
            List<String> page = MatchService.getIds(summoner, queue, 0, 100, 0, null);
            while (page != null && !page.isEmpty()) {
                matchIds.addAll(page);
                for (String matchId : page) {
                    if (LeagueHandler.isMatchDBCached(matchId)) continue;
                    LOLMatch match = MatchService.getRiotMatch(matchId, summoner.getPlatform());
                    if (match != null) MatchService.persistRawAsync(match, JobPriority.BACKGROUND);
                }
                page = MatchService.getIds(summoner, queue, matchIds.size(), 100, 0, null);
            }
        } catch (Exception exception) {
            BotLogger.error("Match history import failed for puuid=" + summoner.getPUUID() + " message=" + exception.getMessage());
        }
    }

    // ============================================================================

    private static Match lookup(Job<?> task, LeagueShard shard, String gameId) {
        task.phase("LOOKUP");
        task.trackItem(gameId);
        for (int retry = 0; retry < MATCH_LOOKUP_MAX_RETRIES; retry++) {
            try {
                LOLMatch source = MatchService.getRiotMatch(gameId, shard);
                Match match = MatchService.persistRaw(source);
                if (match != null) {
                    task.done(gameId);
                    return match;
                }
            } catch (Exception exception) {
                BotLogger.error("Match lookup failed for game=" + gameId + " message=" + exception.getMessage());
            }
        }
        task.missing(gameId);
        return null;
    }

    private static void enqueueMissingMatches(LeagueShard shard, List<String> matchIds, Throwable failure) {
        if (failure != null) throw new IllegalStateException("Recent matches lookup failed", failure);
        if (matchIds == null) return;
        for (String matchId : matchIds) {
            if (MongoDB.hasMatch(matchId)) continue;
            enqueueLookup(MatchUtils.matchShard(matchId, shard), matchId);
        }
    }
}
