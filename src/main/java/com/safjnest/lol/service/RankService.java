package com.safjnest.lol.service;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.queue.RequestPriority;
import com.safjnest.lol.queue.RiotRequestDispatcher;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;

public final class RankService {

    private static final long TRACKER_RANK_REFRESH_WAIT_MILLIS = 500;
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
        if (stored != null) RedisClient.set(RedisKey.SUMMONER_RANKS, stored, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
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
        return refreshAsync(puuid, shard, RequestPriority.IMMEDIATE);
    }

    public static CompletableFuture<List<Rank>> refreshBackgroundAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, RequestPriority.BACKGROUND);
    }

    public static List<Rank> refreshSync(String puuid, LeagueShard shard, RequestPriority priority) {
        if (!valid(puuid, shard)) throw new IllegalArgumentException("A PUUID and shard are required for rank refresh");

        hardInvalidate(puuid, shard);
        waitForTrackerRankRefresh();
        List<LeagueEntry> entries = fetchEntriesFromRiotAsync(
            puuid, shard, "league-entries-tracker-refresh",
            priority == null ? RequestPriority.IMMEDIATE : priority).join();
        List<Rank> ranks = toRanks(entries);
        saveRanks(puuid, shard, ranks, false);
        return ranks;
    }

    private static CompletableFuture<List<Rank>> refreshAsync(
            String puuid,
            LeagueShard shard,
            RequestPriority priority) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        return refreshEntriesFromRiotAsync(puuid, shard, priority).thenApplyAsync(entries -> {
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
            return RiotRequestDispatcher.<List<LeagueEntry>>schedule(RiotRequestDispatcher.request(shard, "league-tier", queue.name() + ":" + tier.name() + ":" + page, () -> {
                List<LeagueEntry> entries = RIOT_API.getLoLAPI().getLeagueAPI()
                    .getLeagueByTierDivision(shard, queue, tier, page);
                return entries == null ? List.of() : entries;
            })).join();
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

        RedisClient.set(RedisKey.R4J_LEAGUE_ENTRIES, entries, shard.name(), entry.getPuuid());
        save(shard, entry);
    }

    public static void save(String puuid, LeagueShard shard, List<LeagueEntry> entries) {
        if (!valid(puuid, shard) || entries == null) return;
        saveRanks(puuid, shard, toRanks(entries));
    }

    public static void save(LeagueShard shard, LeagueEntry entry) {
        if (entry == null || !valid(entry.getPuuid(), shard)) return;

        Rank rank = toRank(entry);
        if (rank == null || !MongoDB.upsertRank(
                entry.getPuuid(), shard, rank, TierDivisionUtils.getMmr(rank.tier(), rank.lp()))) return;

        List<Rank> ranks = replaceRank(find(entry.getPuuid(), shard), rank);
        RedisClient.set(RedisKey.SUMMONER_RANKS, ranks, LeagueShardUtils.cacheRegion(shard), shard.name(), entry.getPuuid());
        ProfileService.invalidate(entry.getPuuid(), shard);
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
        return refreshEntriesFromRiotAsync(puuid, shard, RequestPriority.IMMEDIATE);
    }

    private static CompletableFuture<List<LeagueEntry>> refreshEntriesFromRiotAsync(
            String puuid,
            LeagueShard shard,
            RequestPriority priority) {
        return fetchEntriesFromRiotAsync(puuid, shard, "league-entries-refresh", priority);
    }

    private static CompletableFuture<List<LeagueEntry>> fetchEntriesFromRiotAsync(
        String puuid,
        LeagueShard shard,
        String operation
    ) {
        return fetchEntriesFromRiotAsync(puuid, shard, operation, RequestPriority.IMMEDIATE);
    }

    private static CompletableFuture<List<LeagueEntry>> fetchEntriesFromRiotAsync(
        String puuid,
        LeagueShard shard,
        String operation,
        RequestPriority priority
    ) {
        return RiotRequestDispatcher.schedule(RiotRequestDispatcher.request(shard, operation, puuid, priority, () -> {
            List<LeagueEntry> entries = RIOT_API.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            if (entries == null) throw new IllegalStateException("Riot returned no rank result");
            RedisClient.set(RedisKey.R4J_LEAGUE_ENTRIES, entries, shard.name(), puuid);
            return entries;
        }));
    }

    private static List<Rank> cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.SUMMONER_RANKS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid), RANKS_TYPE);
    }

    private static List<Rank> query(String puuid, LeagueShard shard) {
        return MongoDB.findRanks(puuid, shard);
    }

    private static List<LeagueEntry> cacheEntries(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid), LEAGUE_ENTRIES_TYPE);
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
        RedisClient.set(RedisKey.SUMMONER_RANKS, ranks, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
        if (invalidateProfile) ProfileService.invalidate(puuid, shard);
    }

    private static void hardInvalidate(String puuid, LeagueShard shard) {
        RedisClient.delete(List.of(
            RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid),
            RedisKey.SUMMONER_RANK.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_RANKS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid)
        ));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("platform", shard);
        data.put("id", puuid);
        LeagueHandler.clearCache(URLEndpoint.V4_LEAGUE_ENTRY_BY_PUUID, data);
    }

    private static void waitForTrackerRankRefresh() {
        try {
            Thread.sleep(TRACKER_RANK_REFRESH_WAIT_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before Riot rank refresh", exception);
        }
    }

    private static List<Rank> toRanks(List<LeagueEntry> entries) {
        List<Rank> ranks = new ArrayList<>();
        if (entries == null) return ranks;

        for (LeagueEntry entry : entries) {
            Rank rank = toRank(entry);
            if (rank != null) ranks.add(rank);
        }
        return ranks;
    }

    private static Rank toRank(LeagueEntry entry) {
        if (entry == null || entry.getQueueType() == null) return null;
        return new Rank(
            GameQueueTypeUtils.canonicalQueue(entry.getQueueType()),
            entry.getTierDivisionType(),
            entry.getLeaguePoints(),
            entry.getWins(),
            entry.getLosses()
        );
    }

    private static List<Rank> replaceRank(List<Rank> current, Rank next) {
        List<Rank> ranks = current == null ? new ArrayList<>() : new ArrayList<>(current);
        for (int index = 0; index < ranks.size(); index++) {
            Rank rank = ranks.get(index);
            if (rank != null && rank.queue() == next.queue()) {
                ranks.set(index, next);
                return ranks;
            }
        }
        ranks.add(next);
        return ranks;
    }

    private static Rank unranked(GameQueueType queue) {
        return new Rank(queue, TierDivisionType.UNRANKED, 0, 0, 0);
    }

}
