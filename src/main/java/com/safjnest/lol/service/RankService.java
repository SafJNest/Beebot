package com.safjnest.lol.service;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;

public final class RankService {

    private static final TypeReference<List<LeagueEntry>> LEAGUE_ENTRIES_TYPE =
        new TypeReference<List<LeagueEntry>>() {};
    private static final TypeReference<List<Rank>> RANKS_TYPE = new TypeReference<List<Rank>>() {};

    private static final no.stelar7.api.r4j.impl.R4J RIOT_API = com.safjnest.lol.LeagueHandler.getRiotApi();

    private RankService() {
    }

    public static List<Rank> find(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        List<Rank> cached = cache(puuid, shard);
        if (cached != null) return cached;

        List<Rank> stored = query(puuid, shard);
        if (stored != null) RedisClient.set(RedisKey.PROFILE_RANKS, stored, shard.name(), puuid);
        return stored;
    }

    public static CompletableFuture<List<Rank>> getAsync(String puuid, LeagueShard shard) {
        List<Rank> saved = find(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetch(puuid, shard);
    }

    public static List<Rank> get(String puuid, LeagueShard shard) {
        try {
            return getAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static CompletableFuture<List<Rank>> refreshAsync(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return refreshEntriesFromRiotAsync(puuid, shard).thenApplyAsync(entries -> {
            List<Rank> ranks = toRanks(entries);
            saveRanks(puuid, shard, ranks, false);
            return ranks;
        });
    }

    public static Rank getByQueue(String puuid, LeagueShard shard, GameQueueType queue) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(
            queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue);
        for (Rank rank : get(puuid, shard)) {
            if (rank.queue() == selectedQueue) return rank;
        }
        return unranked(selectedQueue);
    }

    public static List<LeagueEntry> getEntries(String puuid, LeagueShard shard) {
        try {
            return getEntriesAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return new ArrayList<>();
        }
    }

    public static LeagueEntry getEntry(String puuid, LeagueShard shard, String queueCommonName) {
        for (LeagueEntry entry : getEntries(puuid, shard)) {
            if (entry.getQueueType().commonName().equals(queueCommonName)) return entry;
        }
        return null;
    }

    public static List<LeagueEntry> getByTier(
            LeagueShard shard,
            GameQueueType queue,
            TierDivisionType tier,
            int page) {
        if (shard == null || queue == null || tier == null || page < 0) return List.of();

        try {
            return R4JQueue.<List<LeagueEntry>>submit(shard, "league-tier", queue.name() + ":" + tier.name() + ":" + page, () -> {
                List<LeagueEntry> entries = RIOT_API.getLoLAPI().getLeagueAPI()
                    .getLeagueByTierDivision(shard, queue, tier, page);
                return entries == null ? List.of() : entries;
            }).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static void put(LeagueShard shard, LeagueEntry entry) {
        if (entry == null || shard == null || entry.getPuuid() == null) return;

        List<LeagueEntry> entries = cacheEntries(entry.getPuuid(), shard);
        if (entries == null) entries = new ArrayList<>();

        boolean updated = false;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).getQueueType() == entry.getQueueType()) {
                entries.set(index, entry);
                updated = true;
                break;
            }
        }
        if (!updated) entries.add(entry);

        RedisClient.set(RedisKey.LEAGUE_ENTRIES, entries, shard.name(), entry.getPuuid());
        if (SummonerService.get(entry.getPuuid(), shard) != null) save(entry.getPuuid(), shard, entries);
    }

    public static void save(String puuid, LeagueShard shard, List<LeagueEntry> entries) {
        if (!valid(puuid, shard) || entries == null) return;
        saveRanks(puuid, shard, toRanks(entries));
    }

    // ============================================================================

    private static CompletableFuture<List<Rank>> fetch(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return SummonerService.getAsync(puuid, shard).thenCompose(summoner -> {
            if (summoner == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Summoner is not available for rank persistence"));
            return getEntriesAsync(puuid, shard).thenApplyAsync(entries -> {
                List<Rank> ranks = toRanks(entries);
                saveRanks(puuid, shard, ranks, true);
                return ranks;
            });
        });
    }

    private static CompletableFuture<List<LeagueEntry>> getEntriesAsync(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        List<LeagueEntry> cached = cacheEntries(puuid, shard);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return getEntriesFromRiotAsync(puuid, shard);
    }

    private static CompletableFuture<List<LeagueEntry>> getEntriesFromRiotAsync(String puuid, LeagueShard shard) {
        return fetchEntriesFromRiotAsync(puuid, shard, "league-entries");
    }

    private static CompletableFuture<List<LeagueEntry>> refreshEntriesFromRiotAsync(String puuid, LeagueShard shard) {
        return fetchEntriesFromRiotAsync(puuid, shard, "league-entries-refresh");
    }

    private static CompletableFuture<List<LeagueEntry>> fetchEntriesFromRiotAsync(
        String puuid,
        LeagueShard shard,
        String operation
    ) {
        return R4JQueue.<List<LeagueEntry>>submit(shard, operation, puuid, () -> {
            List<LeagueEntry> entries = RIOT_API.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            if (entries == null) throw new IllegalStateException("Riot returned no rank result");
            RedisClient.set(RedisKey.LEAGUE_ENTRIES, entries, shard.name(), puuid);
            return entries;
        });
    }

    private static List<Rank> cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.PROFILE_RANKS.of(shard.name(), puuid), RANKS_TYPE);
    }

    private static List<Rank> query(String puuid, LeagueShard shard) {
        return MongoDB.findRanks(puuid, shard);
    }

    private static List<LeagueEntry> cacheEntries(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid), LEAGUE_ENTRIES_TYPE);
    }

    private static void saveRanks(String puuid, LeagueShard shard, List<Rank> ranks) {
        saveRanks(puuid, shard, ranks, true);
    }

    private static void saveRanks(String puuid, LeagueShard shard, List<Rank> ranks, boolean invalidateProfile) {
        if (!valid(puuid, shard) || ranks == null) return;

        Map<GameQueueType, Long> mmr = new HashMap<>();
        for (Rank rank : ranks) {
            if (rank != null && rank.queue() != null) {
                mmr.put(rank.queue(), (long) TierDivisionUtils.getMmr(rank.tier(), rank.lp()));
            }
        }
        MongoDB.upsertRanks(puuid, shard, ranks, mmr);
        RedisClient.set(RedisKey.PROFILE_RANKS, ranks, shard.name(), puuid);
        if (invalidateProfile) ProfileService.invalidate(puuid, shard);
    }

    private static List<Rank> toRanks(List<LeagueEntry> entries) {
        List<Rank> ranks = new ArrayList<>();
        if (entries == null) return ranks;

        for (LeagueEntry entry : entries) {
            if (entry == null || entry.getQueueType() == null) continue;
            ranks.add(new Rank(
                GameQueueTypeUtils.canonicalQueue(entry.getQueueType()),
                entry.getTierDivisionType(),
                entry.getLeaguePoints(),
                entry.getWins(),
                entry.getLosses()
            ));
        }
        return ranks;
    }

    private static Rank unranked(GameQueueType queue) {
        return new Rank(queue, TierDivisionType.UNRANKED, 0, 0, 0);
    }

}
