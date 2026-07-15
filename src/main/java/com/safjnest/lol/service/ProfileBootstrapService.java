package com.safjnest.lol.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.safjnest.sql.database.LeagueDB;
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

            List<LeagueEntry> entries = LeagueService.getLeagueEntries(puuid, shard);
            LeagueDB.updateSummonerEntries(summonerId, entries, shard);
            LeagueService.invalidateSummoner(puuid, shard);
        } catch (Exception exception) {
            BotLogger.error("Profile bootstrap failed for " + key + ": " + exception.getMessage());
        } finally {
            PENDING.remove(key);
        }
    }
}
