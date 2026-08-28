package com.safjnest.lol.service;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;
import com.safjnest.lol.queue.job.JobPriority;
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

    private static final TypeReference<List<LeagueEntry>> LEAGUE_ENTRIES_TYPE =
        new TypeReference<List<LeagueEntry>>() {};
    private static final TypeReference<Map<GameQueueType, Rank>> RANKS_TYPE = new TypeReference<>() {};

    private static final no.stelar7.api.r4j.impl.R4J RIOT_API = com.safjnest.lol.LeagueHandler.getRiotApi();

    private RankService() {
    }

    public static Map<GameQueueType, Rank> find(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        Map<GameQueueType, Rank> cached = cache(puuid, shard);
        if (cached != null) return cached;

        Map<GameQueueType, Rank> stored = query(puuid, shard);
        if (stored != null) RedisClient.set(RedisKey.SUMMONER_RANKS, stored, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
        return stored;
    }

    public static CompletableFuture<Map<GameQueueType, Rank>> getAsync(String puuid, LeagueShard shard) {
        Map<GameQueueType, Rank> saved = find(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetch(puuid, shard);
    }

    public static Map<GameQueueType, Rank> get(String puuid, LeagueShard shard) {
        try {
            return getAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return Map.of();
        }
    }

    public static CompletableFuture<Map<GameQueueType, Rank>> refreshAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, JobPriority.IMMEDIATE);
    }

    public static CompletableFuture<Map<GameQueueType, Rank>> refreshBackgroundAsync(String puuid, LeagueShard shard) {
        return refreshAsync(puuid, shard, JobPriority.BACKGROUND);
    }

    public static Map<GameQueueType, Rank> refreshSync(String puuid, LeagueShard shard, JobPriority priority) {
        if (!valid(puuid, shard)) throw new IllegalArgumentException("A PUUID and shard are required for rank refresh");

        hardInvalidate(puuid, shard);
        List<LeagueEntry> entries = refreshEntriesFromRiotAsync(
            puuid, shard, priority == null ? JobPriority.IMMEDIATE : priority).join();
        Map<GameQueueType, Rank> ranks = toRanks(entries);
        saveRanks(puuid, shard, ranks, false);
        return ranks;
    }

    private static CompletableFuture<Map<GameQueueType, Rank>> refreshAsync(
            String puuid,
            LeagueShard shard,
            JobPriority priority) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(Map.of());

        return refreshEntriesFromRiotAsync(puuid, shard, priority).thenApplyAsync(entries -> {
            Map<GameQueueType, Rank> ranks = toRanks(entries);
            saveRanks(puuid, shard, ranks, false);
            return ranks;
        });
    }

    public static Rank getByQueue(String puuid, LeagueShard shard, GameQueueType queue) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(
            queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue);
        Rank rank = get(puuid, shard).get(selectedQueue);
        return rank == null ? Rank.unranked() : rank;
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
            String id = queue.name() + ":" + tier.name() + ":" + page;
            return QueueHandler.<List<LeagueEntry>>immediate(RiotScheduler.class, shard, shard.name() + ":league-tier:" + id,
                "league tier " + id, ignored -> {
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

        RedisClient.set(RedisKey.R4J_LEAGUE_ENTRIES, entries, shard.name(), entry.getPuuid());
        save(shard, entry);
    }

    public static void saveEntry(LeagueShard shard, LeagueEntry entry) {
        if (entry == null || !valid(entry.getPuuid(), shard)) return;
        if (SummonerService.find(entry.getPuuid(), shard) == null) {
            var summoner = SummonerService.getRiotSummoner(entry.getPuuid(), shard);
            if (summoner == null) return;
            if (SummonerService.find(entry.getPuuid(), shard) == null && !SummonerService.upsert(summoner, null)) return;
        }
        put(shard, entry);
    }

    public static void enqueueRankEntries(boolean highElo, boolean allEntries) {
        List<TierDivisionType> highTiers = highElo ? highEloTiers() : List.of();
        List<TierDivisionType> allTiers = allEntries ? allEntryTiers() : List.of();
        QueueHandler.background(SyncScheduler.class, null, "rank-entries", "RANK_ENTRIES", root -> {
            for (LeagueShard shard : LeagueShardUtils.getActives()) {
                for (TierDivisionType tier : highTiers) for (GameQueueType queue : List.of(GameQueueType.RANKED_SOLO_5X5, GameQueueType.RANKED_FLEX_SR))
                    enqueueTier(shard, tier, queue, 0);
                for (TierDivisionType tier : allTiers) enqueueTier(shard, tier, GameQueueType.RANKED_SOLO_5X5, -1);
            }
            return null;
        });
    }

    public static void save(String puuid, LeagueShard shard, List<LeagueEntry> entries) {
        if (!valid(puuid, shard) || entries == null) return;
        saveRanks(puuid, shard, toRanks(entries));
    }

    public static void save(LeagueShard shard, LeagueEntry entry) {
        if (entry == null || !valid(entry.getPuuid(), shard)) return;

        GameQueueType queue = GameQueueTypeUtils.canonicalQueue(entry.getQueueType());
        Rank rank = toRank(entry);
        if (rank == null || !MongoDB.upsertRank(
                entry.getPuuid(), shard, queue, rank, TierDivisionUtils.getMmr(rank.tier(), rank.lp()))) return;

        Map<GameQueueType, Rank> ranks = replaceRank(find(entry.getPuuid(), shard), queue, rank);
        RedisClient.set(RedisKey.SUMMONER_RANKS, ranks, LeagueShardUtils.cacheRegion(shard), shard.name(), entry.getPuuid());
        ProfileService.invalidate(entry.getPuuid(), shard);
    }

    // ============================================================================

    private static CompletableFuture<Map<GameQueueType, Rank>> fetch(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(Map.of());

        return SummonerService.getAsync(puuid, shard).thenCompose(summoner -> {
            if (summoner == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Summoner is not available for rank persistence"));
            return getEntriesAsync(puuid, shard).thenApplyAsync(entries -> {
                Map<GameQueueType, Rank> ranks = toRanks(entries);
                saveRanks(puuid, shard, ranks, true);
                return ranks;
            });
        });
    }

    private static void enqueueTier(LeagueShard shard, TierDivisionType tier, GameQueueType queue, int page) {
        String key = (page == 0 ? "rank-high:" : "rank-all:") + shard.name() + ':' + tier.name() + ':' + queue.name();
        QueueHandler.background(SyncScheduler.class, shard, key, "rank entries tier=" + tier.name() + " queue=" + queue.name(), task -> {
            int currentPage = page;
            do {
                task.phase("FETCHING");
                List<LeagueEntry> entries = getByTier(shard, queue, tier, currentPage);
                task.phase("PERSISTING");
                for (LeagueEntry entry : entries) {
                    task.trackItem(entry.getPuuid());
                    try {
                        saveEntry(shard, entry);
                        task.done(entry.getPuuid());
                    } catch (Exception exception) {
                        task.failed(entry.getPuuid());
                    }
                }
                if (page == 0 || entries.isEmpty()) break;
                currentPage++;
            } while (true);
            return null;
        });
    }

    private static List<TierDivisionType> highEloTiers() {
        List<TierDivisionType> result = new ArrayList<>();
        for (TierDivisionType tier : TierDivisionType.values()) if (TierDivisionUtils.isHighElo(tier)) result.add(tier);
        return result;
    }

    private static List<TierDivisionType> allEntryTiers() {
        List<TierDivisionType> result = new ArrayList<>(List.of(TierDivisionType.values()));
        Collections.reverse(result);
        result.remove(0);
        result.removeIf(tier -> TierDivisionUtils.isHighElo(tier) || tier == TierDivisionType.UNRANKED);
        return result;
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

    private static CompletableFuture<List<LeagueEntry>> refreshEntriesFromRiotAsync(
            String puuid,
            LeagueShard shard,
            JobPriority priority) {
        return fetchEntriesFromRiotAsync(puuid, shard, "league-entries-refresh", priority);
    }

    private static CompletableFuture<List<LeagueEntry>> fetchEntriesFromRiotAsync(
        String puuid,
        LeagueShard shard,
        String operation
    ) {
        return fetchEntriesFromRiotAsync(puuid, shard, operation, JobPriority.IMMEDIATE);
    }

    private static CompletableFuture<List<LeagueEntry>> fetchEntriesFromRiotAsync(
        String puuid,
        LeagueShard shard,
        String operation,
        JobPriority priority
    ) {
        return switch (priority) {
            case IMMEDIATE -> QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ':' + operation + ':' + puuid,
                operation + " puuid=" + puuid, ignored -> fetchEntries(shard, puuid));
            case NORMAL -> QueueHandler.normal(RiotScheduler.class, shard, shard.name() + ':' + operation + ':' + puuid,
                operation + " puuid=" + puuid, ignored -> fetchEntries(shard, puuid));
            case BACKGROUND -> QueueHandler.background(RiotScheduler.class, shard, shard.name() + ':' + operation + ':' + puuid,
                operation + " puuid=" + puuid, ignored -> fetchEntries(shard, puuid));
        };
    }

    private static List<LeagueEntry> fetchEntries(LeagueShard shard, String puuid) {
            List<LeagueEntry> entries = RIOT_API.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            if (entries == null) throw new IllegalStateException("Riot returned no rank result");
            RedisClient.set(RedisKey.R4J_LEAGUE_ENTRIES, entries, shard.name(), puuid);
            return entries;
    }

    private static Map<GameQueueType, Rank> cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.SUMMONER_RANKS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid), RANKS_TYPE);
    }

    private static Map<GameQueueType, Rank> query(String puuid, LeagueShard shard) {
        return MongoDB.findRanks(puuid, shard);
    }

    private static List<LeagueEntry> cacheEntries(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid), LEAGUE_ENTRIES_TYPE);
    }

    private static void saveRanks(String puuid, LeagueShard shard, Map<GameQueueType, Rank> ranks) {
        saveRanks(puuid, shard, ranks, true);
    }

    private static void saveRanks(String puuid, LeagueShard shard, Map<GameQueueType, Rank> ranks, boolean invalidateProfile) {
        if (!valid(puuid, shard) || ranks == null) return;

        Map<GameQueueType, Long> mmr = new LinkedHashMap<>();
        for (Map.Entry<GameQueueType, Rank> entry : ranks.entrySet()) if (entry.getKey() != null && entry.getValue() != null)
            mmr.put(entry.getKey(), (long) TierDivisionUtils.getMmr(entry.getValue().tier(), entry.getValue().lp()));
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

    private static Map<GameQueueType, Rank> toRanks(List<LeagueEntry> entries) {
        Map<GameQueueType, Rank> ranks = new LinkedHashMap<>();
        if (entries == null) return ranks;

        for (LeagueEntry entry : entries) {
            Rank rank = toRank(entry);
            if (rank != null) ranks.put(GameQueueTypeUtils.canonicalQueue(entry.getQueueType()), rank);
        }
        return ranks;
    }

    private static Rank toRank(LeagueEntry entry) {
        if (entry == null || entry.getQueueType() == null) return null;
        return new Rank(
            entry.getTierDivisionType(),
            entry.getLeaguePoints(),
            entry.getWins(),
            entry.getLosses()
        );
    }

    private static Map<GameQueueType, Rank> replaceRank(
            Map<GameQueueType, Rank> current,
            GameQueueType queue,
            Rank next) {
        Map<GameQueueType, Rank> ranks = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
        ranks.put(queue, next);
        return ranks;
    }

}
