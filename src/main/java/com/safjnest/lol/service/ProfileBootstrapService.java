package com.safjnest.lol.service;

import com.safjnest.mongo.MongoDB;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.safjnest.sql.database.LeagueDB;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;

public final class ProfileBootstrapService {

    private static final int BOOTSTRAP_THREADS = 2;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
        BOOTSTRAP_THREADS,
        runnable -> {
            Thread thread = new Thread(runnable, "profile-bootstrap");
            thread.setDaemon(true);
            return thread;
        }
    );
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private ProfileBootstrapService() {}

    public static void enqueue(LeagueShard shard, String puuid) {
        if (shard == null || puuid == null || puuid.isBlank()) return;

        String key = shard.name() + ":" + puuid;
        if (PENDING.add(key)) CompletableFuture.runAsync(() -> bootstrap(shard, puuid, key), EXECUTOR);
    }

    // ============================================================================

    private static void bootstrap(LeagueShard shard, String puuid, String key) {
        try {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner = LeagueService.getSummonerByPuuid(puuid, shard);
            if (summoner == null) return;

            int summonerId = LeagueDB.addLOLAccount(summoner);
            if (summonerId == 0) return;

            com.safjnest.lol.model.summoner.Summoner profile =
                    LeagueService.getProfileBaseFromDatabase(puuid, shard);
            if (profile != null) MongoDB.upsertSummoner(profile, null);

            List<LeagueEntry> entries = LeagueService.getLeagueEntries(puuid, shard);
            LeagueDB.updateSummonerEntries(puuid, summonerId, entries, shard);
            List<Rank> ranks = new java.util.ArrayList<>();
            java.util.Map<no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType, Long> mmr = new java.util.HashMap<>();
            for (LeagueEntry entry : entries) {
                no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType queue =
                        GameQueueTypeUtils.canonicalQueue(entry.getQueueType());
                ranks.add(new Rank(queue, entry.getTierDivisionType(), entry.getLeaguePoints(), entry.getWins(), entry.getLosses()));
                mmr.put(queue, (long) TierDivisionUtils.getMmr(entry.getTierDivisionType(), entry.getLeaguePoints()));
            }
            MongoDB.upsertRanks(puuid, shard, ranks, mmr);
            LeagueService.invalidateSummoner(puuid, shard);
        } catch (Exception exception) {
            BotLogger.error("Profile bootstrap failed for " + key + ": " + exception.getMessage());
        } finally {
            PENDING.remove(key);
        }
    }
}
