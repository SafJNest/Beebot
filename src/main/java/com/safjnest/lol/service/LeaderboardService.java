package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerLeaderboard;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueConstants;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import org.bson.types.Binary;

public class LeaderboardService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = DEFAULT_PAGE_SIZE;
    private static final Map<String, Object> LEADERBOARD_COUNT_LOCKS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Boolean>> INDEX_BUILDS = new ConcurrentHashMap<>();

    private final ProfileService profileService = new ProfileService();

    public ApiResult<LeaderboardPage> getLeaderboard(
        TierType rank, GameQueueType queue, LeagueShard region, LaneType role, int page, int limit
    ) {
        return getLeaderboard(rank, queue, region, role, null, page, limit);
    }

    public ApiResult<LeaderboardPage> getLeaderboard(
        TierType rank, GameQueueType queue, LeagueShard region, LaneType role, Integer otpChampionId, int page, int limit
    ) {
        if (page < 1) throw new IllegalArgumentException("page must be greater than 0");
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("limit must be between 1 and 50");

        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(GameQueueTypeUtils.defaultQueue(queue));
        String selectedRegion = LeagueShardUtils.defaultRegion(region);
        String rankKey = rank == null ? LeagueConstants.ALL : rank.name();
        String roleKey = role == null ? LeagueConstants.ALL : role.name();
        String otpKey = otpChampionId == null ? LeagueConstants.ALL : otpChampionId.toString();
        String key = RedisKey.LEADERBOARD_PAGE.of(
            rankKey, selectedQueue.name(), selectedRegion, roleKey, otpKey, page, limit
        );
        LeaderboardPage cached = RedisClient.get(key, LeaderboardPage.class);
        if (cached != null) return ApiResult.ready(cached);

        long offset = (long) (page - 1) * limit;
        List<Summoner> summoners = MongoDB.findLeaderboardPage(
            rank, selectedQueue, selectedRegion, role, otpChampionId, offset, limit
        );
        long total = findLeaderboardCount(rank, selectedQueue, selectedRegion, role, otpChampionId);
        long pages = total == 0 ? 0 : (total + limit - 1) / limit;

        Filter filter = Filter.canonical();
        Map<String, ProfileStatistics> statisticsBySummoner = new HashMap<>();
        Map<LeagueShard, List<String>> puuidsByShard = new HashMap<>();
        for (Summoner summoner : summoners) {
            LeagueShard shard = summoner.region();
            puuidsByShard.computeIfAbsent(shard, ignored -> new ArrayList<>()).add(summoner.puuid());
        }
        for (Map.Entry<LeagueShard, List<String>> entry : puuidsByShard.entrySet())
            statisticsBySummoner.putAll(profileService.getStatistics(entry.getValue(), entry.getKey(), filter));
        for (Summoner summoner : summoners) {
            if (statisticsBySummoner.containsKey(summoner.puuid())) continue;
            ComputeScheduler.startProfileStatistics(summoner, filter);
        }

        List<RankedSummoner> rankingSubjects = new ArrayList<>(summoners.size());
        for (Summoner summoner : summoners) {
            Rank rankValue = summoner.ranks().get(selectedQueue);
            if (rankValue == null) rankValue = Rank.unranked();
            rankingSubjects.add(new RankedSummoner(
                summoner.puuid(), summoner.region(), Map.of(selectedQueue, rankValue)));
        }
        Map<String, Map<GameQueueType, Rank>> rankings = resolveRankings(rankingSubjects);

        List<SummonerLeaderboard> leaderboardSummoners = new ArrayList<>(summoners.size());
        for (int index = 0; index < summoners.size(); index++) {
            Summoner summoner = summoners.get(index);
            Rank rankValue = summoner.ranks().get(selectedQueue);
            if (rankValue == null) rankValue = Rank.unranked();
            Map<GameQueueType, Rank> ranks = rankings.getOrDefault(summoner.puuid(), Map.of(selectedQueue, rankValue));
            ProfileStatistics statistics = statisticsBySummoner.get(summoner.puuid());
            SummonerView view = SummonerView.from(
                summoner,
                ranks,
                statistics,
                statistics == null ? List.of() : summoner.masteries()
            );
            leaderboardSummoners.add(new SummonerLeaderboard(offset + index + 1, view));
        }

        LeaderboardPage response = new LeaderboardPage(page, limit, total, pages, leaderboardSummoners);
        if (statisticsBySummoner.size() == summoners.size()) RedisClient.set(
            RedisKey.LEADERBOARD_PAGE,
            response,
            rankKey, selectedQueue.name(), selectedRegion, roleKey, otpKey, page, limit
        );
        return ApiResult.ready(response);
    }

    public LeaderboardDistribution getRankDistribution(GameQueueType queue, LeagueShard region) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(GameQueueTypeUtils.defaultQueue(queue));
        String selectedRegion = LeagueShardUtils.defaultRegion(region);
        String key = RedisKey.LEADERBOARD_RANK_DISTRIBUTION.of(
            selectedQueue.name(), selectedRegion
        );
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB.findRankDistribution(selectedQueue, selectedRegion);
        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(
            RedisKey.LEADERBOARD_RANK_DISTRIBUTION,
            response,
            selectedQueue.name(), selectedRegion
        );
        return response;
    }

    public LeaderboardDistribution getTopRegions(GameQueueType queue, TierType rank) {
        requireRank(rank);
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(GameQueueTypeUtils.defaultQueue(queue));
        String key = RedisKey.LEADERBOARD_TOP_REGIONS.of(
            selectedQueue.name(), rank.name()
        );
        LeaderboardDistribution cached = RedisClient.get(key, LeaderboardDistribution.class);
        if (cached != null) return cached;

        List<LeaderboardDistribution.Entry> entries = MongoDB.findTopRegions(selectedQueue, rank);
        LeaderboardDistribution response = new LeaderboardDistribution(entries);
        RedisClient.set(
            RedisKey.LEADERBOARD_TOP_REGIONS,
            response,
            selectedQueue.name(), rank.name()
        );
        return response;
    }

    public void rebuildHighEloAndTrackedProfileStatistics() {
        Filter filter = Filter.canonical();
        Set<String> processedPuuids = new HashSet<>();

        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            for (GameQueueType queue : GameQueueTypeUtils.profileRebuildQueues()) {
                for (TierType rank : TierDivisionUtils.profileRebuildTiers()) {
                    long offset = 0;
                    while (true) {
                        List<Summoner> page = MongoDB.findLeaderboardPage(
                            rank, queue, shard.name(), offset, DEFAULT_PAGE_SIZE
                        );
                        if (page.isEmpty()) break;

                        rebuildProfilePage(page, filter, processedPuuids);
                        offset += page.size();
                        if (page.size() < DEFAULT_PAGE_SIZE) break;
                    }
                }
            }
        }

        List<Summoner> tracked = MongoDB.findTrackedSummonerModels();
        rebuildProfilePage(tracked, filter, processedPuuids);

    }

    public static void rebuild() {
        MongoDB.rebuildLeaderboardAggregates();
        warmupIndex();
    }

    public static MongoDB.LeaderboardAggregateRebuild rebuildAllAggregates() {
        MongoDB.LeaderboardAggregateRebuild report = MongoDB.rebuildAllLeaderboardAggregates();
        warmupIndex();
        return report;
    }

    public static Map<GameQueueType, Rank> resolveRankings(
        String puuid,
        LeagueShard region,
        Map<GameQueueType, Rank> ranks
    ) {
        if (puuid == null || puuid.isBlank()) return ranks == null ? Map.of() : Map.copyOf(ranks);
        return resolveRankings(List.of(new RankedSummoner(puuid, region, ranks))).getOrDefault(puuid,
            ranks == null ? Map.of() : Map.copyOf(ranks));
    }

    public static Map<String, Map<GameQueueType, Rank>> resolveRankings(List<RankedSummoner> subjects) {
        Map<String, Map<GameQueueType, Rank>> result = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        List<byte[]> members = new ArrayList<>();
        List<RankTarget> targets = new ArrayList<>();
        Set<RankSegment> segments = new LinkedHashSet<>();
        Map<String, Map<TierDivisionType, Long>> counts = new HashMap<>();

        if (subjects == null) return result;
        for (RankedSummoner subject : subjects) {
            if (subject == null || subject.puuid() == null || subject.puuid().isBlank() || subject.ranks() == null) continue;
            Map<GameQueueType, Rank> enriched = new LinkedHashMap<>(subject.ranks());
            result.put(subject.puuid(), enriched);
            if (subject.region() == null) continue;
            for (Map.Entry<GameQueueType, Rank> entry : subject.ranks().entrySet()) {
                GameQueueType queue = entry.getKey() == null ? null : GameQueueTypeUtils.canonicalQueue(entry.getKey());
                Rank rank = entry.getValue();
                if (queue == null || rank == null || rank.tier() == null || rank.tier() == TierDivisionType.UNRANKED) continue;
                String globalSegment = RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.of(queue.name(), LeagueShardUtils.leaderboardScope(null), rank.tier().name());
                String regionalSegment = RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.of(queue.name(), LeagueShardUtils.leaderboardScope(subject.region()), rank.tier().name());
                segments.add(new RankSegment(queue, rank.tier(), null));
                segments.add(new RankSegment(queue, rank.tier(), subject.region()));
                Map<TierDivisionType, Long> globalCounts = counts.computeIfAbsent(
                    RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(queue.name(), LeagueShardUtils.leaderboardScope(null)),
                    ignored -> counts(queue, null));
                Map<TierDivisionType, Long> regionalCounts = counts.computeIfAbsent(
                    RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(queue.name(), LeagueShardUtils.leaderboardScope(subject.region())),
                    ignored -> counts(queue, subject.region()));
                targets.add(new RankTarget(subject.puuid(), queue, rank, globalCounts, regionalCounts));
                byte[] member = member(subject.puuid(), queue);
                keys.add(globalSegment); members.add(member);
                keys.add(regionalSegment); members.add(member);
            }
        }

        ensureIndexesAsync(segments);
        List<Long> positions = RedisClient.reverseRanks(keys, members);
        for (int index = 0; index + 1 < positions.size(); index += 2) {
            RankTarget target = targets.get(index / 2);
            Map<GameQueueType, Rank> enriched = result.get(target.puuid());
            if (enriched != null) enriched.put(target.queue(), target.rank().withRankings(
                absoluteRanking(target.globalCounts(), target.rank().tier(), positions.get(index)),
                absoluteRanking(target.regionalCounts(), target.rank().tier(), positions.get(index + 1))));
        }
        Map<String, Map<GameQueueType, Rank>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<GameQueueType, Rank>> entry : result.entrySet()) immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        return Map.copyOf(immutable);
    }

    static void updateIndex(CompetitiveEntry previous, CompetitiveEntry next) {
        applyIndexChange(previous, -1);
        applyIndexChange(next, 1);
    }

    public static void warmupIndexAsync() {
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "leaderboard-index-warmup", "leaderboard index warmup", job -> {
            warmupIndex();
            return null;
        });
    }

    public static void warmupIndex() {
        for (GameQueueType queue : GameQueueTypeUtils.leaderboardQueues()) {
            replaceCounts(queue, null);
            List<LeagueShard> regions = MongoDB.findCompetitiveRankingRegions(queue);
            for (LeagueShard region : regions) replaceCounts(queue, region);
            for (TierDivisionType tier : TierDivisionUtils.descendingRankedDivisions()) if (TierDivisionUtils.isHighElo(tier)) {
                rebuildIndex(new RankSegment(queue, tier, null));
                for (LeagueShard region : regions) rebuildIndex(new RankSegment(queue, tier, region));
            }
        }
    }

    public static IndexStatus indexStatus() {
        List<SegmentStatus> segments = new ArrayList<>();
        long memory = 0;
        Map<String, Long> accesses = RedisClient.getHashLongs(RedisKey.CONTEXTUAL_LEADERBOARD_ACCESS.of());
        for (String key : RedisClient.members(RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENTS.of())) {
            if (!RedisClient.sortedSetExists(key)) continue;
            Long usage = RedisClient.memoryUsage(key);
            long used = usage == null ? 0 : usage;
            memory += used;
            long ttl = RedisClient.ttl(key);
            segments.add(new SegmentStatus(key, RedisClient.sortedSetCardinality(key), used, ttl == -1, ttl,
                accesses.getOrDefault(key, 0L), INDEX_BUILDS.containsKey(key)));
        }
        return new IndexStatus(segments.size(), memory, List.copyOf(segments));
    }

    public record RankedSummoner(String puuid, LeagueShard region, Map<GameQueueType, Rank> ranks) {}
    public record SegmentStatus(String key, long cardinality, long memoryBytes, boolean permanent, long ttlSeconds, long lastAccess, boolean rebuilding) {}
    public record IndexStatus(long segments, long memoryBytes, List<SegmentStatus> values) {}

    // ============================================================================

    private static long findLeaderboardCount(
        TierType rank, GameQueueType queue, String region, LaneType role, Integer otpChampionId
    ) {
        String rankKey = rank == null ? LeagueConstants.ALL : rank.name();
        String roleKey = role == null ? LeagueConstants.ALL : role.name();
        String otpKey = otpChampionId == null ? LeagueConstants.ALL : otpChampionId.toString();
        String cacheKey = RedisKey.LEADERBOARD_COUNT.of(queue.name(), region, rankKey, roleKey, otpKey);
        Long cached = RedisClient.getLong(cacheKey);
        if (cached != null && cached >= 0) return cached;

        Object lock = LEADERBOARD_COUNT_LOCKS.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            cached = RedisClient.getLong(cacheKey);
            if (cached != null && cached >= 0) return cached;

            boolean claimed = RedisClient.claim(RedisKey.LEADERBOARD_COUNT_LOCK, "1", queue.name(), region, rankKey, roleKey, otpKey);
            try {
                Long aggregate = role == null && otpChampionId == null ? MongoDB.findLeaderboardAggregateCount(rank, queue, region) : null;
                long total = aggregate == null
                    ? MongoDB.findLeaderboardCount(rank, queue, region, role, otpChampionId)
                    : aggregate;
                RedisClient.setCached(cacheKey, Long.toString(total), RedisKey.LEADERBOARD_COUNT.ttlSeconds());
                return total;
            } finally {
                if (claimed) RedisClient.delete(RedisKey.LEADERBOARD_COUNT_LOCK.of(queue.name(), region, rankKey, roleKey, otpKey));
            }
        }
    }

    private static void requireRank(TierType rank) {
        if (rank == null) throw new IllegalArgumentException("rank is required");
    }

    private int rebuildProfilePage(List<Summoner> summoners, Filter filter, Set<String> processedPuuids) {
        List<Summoner> selected = new ArrayList<>(summoners.size());
        for (Summoner summoner : summoners) {
            if (summoner != null && summoner.puuid() != null && processedPuuids.add(summoner.puuid()))
                selected.add(summoner);
        }

        List<CompletableFuture<Boolean>> refreshes = new ArrayList<>();
        for (Summoner summoner : selected) {
            refreshes.add(ComputeScheduler.startProfileStatistics(summoner, filter, true));
        }

        for (int index = 0; index < refreshes.size(); index++) {
            CompletableFuture<Boolean> refresh = refreshes.get(index);
            try {
                if (!refresh.join()) processedPuuids.remove(selected.get(index).puuid());
            } catch (RuntimeException exception) {
                processedPuuids.remove(selected.get(index).puuid());
                BotLogger.error("High elo/tracked profile statistics rebuild failed: " + exception.getMessage());
            }
        }
        return refreshes.size();
    }

    private static void applyIndexChange(CompetitiveEntry entry, long delta) {
        if (entry == null || entry.tier() == null || entry.tier() == TierDivisionType.UNRANKED) return;
        GameQueueType queue = GameQueueTypeUtils.canonicalQueue(entry.queue());
        List<String> scopes = new ArrayList<>(2);
        scopes.add(LeagueShardUtils.leaderboardScope(null));
        if (entry.region() != null) scopes.add(LeagueShardUtils.leaderboardScope(entry.region()));
        for (String scope : scopes) {
            RedisClient.incrementHashIfPresent(
                RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(queue.name(), scope), entry.tier().name(), delta);
            String key = RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.of(queue.name(), scope, entry.tier().name());
            if (!RedisClient.sortedSetExists(key)) continue;
            if (delta > 0) RedisClient.addSortedSet(key, List.of(new RedisClient.SortedSetEntry(member(entry.puuid(), queue), entry.mmr())));
            else RedisClient.removeSortedSetMember(key, member(entry.puuid(), queue));
            if (!TierDivisionUtils.isHighElo(entry.tier()))
                RedisClient.expire(key, RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.ttlSeconds());
            RedisClient.setHashLong(RedisKey.CONTEXTUAL_LEADERBOARD_ACCESS.of(), key, System.currentTimeMillis());
        }
    }

    private static void ensureIndexesAsync(Set<RankSegment> segments) {
        if (segments.isEmpty()) return;
        Map<RankSegment, String> keys = new LinkedHashMap<>();
        for (RankSegment segment : segments) keys.put(segment, RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.of(
            GameQueueTypeUtils.canonicalQueue(segment.queue()).name(), LeagueShardUtils.leaderboardScope(segment.region()), segment.tier().name()));
        Set<String> existing = RedisClient.existingSortedSets(new ArrayList<>(keys.values()));
        for (Map.Entry<RankSegment, String> entry : keys.entrySet()) {
            RankSegment segment = entry.getKey();
            String key = entry.getValue();
            if (existing.contains(key)) {
                if (!TierDivisionUtils.isHighElo(segment.tier()))
                    RedisClient.expire(key, RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.ttlSeconds());
                RedisClient.setHashLong(RedisKey.CONTEXTUAL_LEADERBOARD_ACCESS.of(), key, System.currentTimeMillis());
                continue;
            }
            QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "leaderboard-index:" + key,
                "leaderboard index " + key, job -> {
                    rebuildIndex(segment);
                    return null;
                });
        }
    }

    private static void rebuildIndex(RankSegment segment) {
        String key = RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.of(
            GameQueueTypeUtils.canonicalQueue(segment.queue()).name(), LeagueShardUtils.leaderboardScope(segment.region()), segment.tier().name());
        CompletableFuture<Boolean> created = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = INDEX_BUILDS.putIfAbsent(key, created);
        if (existing != null) {
            existing.join();
            return;
        }
        try {
            int ttl = TierDivisionUtils.isHighElo(segment.tier()) ? 0 : RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.ttlSeconds();
            boolean built = RedisClient.buildSortedSet(
                RedisKey.CONTEXTUAL_RANKING_BUILD.of(java.util.UUID.randomUUID()), key, ttl,
                RedisKey.CONTEXTUAL_RANKING_BUILD.ttlSeconds(), entries -> MongoDB.forEachCompetitiveRankingSegment(
                    segment.queue(), segment.tier(), segment.region(), entry ->
                    entries.accept(new RedisClient.SortedSetEntry(member(entry.puuid(), segment.queue()), entry.mmr()))));
            if (built) {
                RedisClient.addPersistentMember(RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENTS.of(), key);
                RedisClient.setHashLong(RedisKey.CONTEXTUAL_LEADERBOARD_ACCESS.of(), key, System.currentTimeMillis());
            }
            created.complete(built);
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            INDEX_BUILDS.remove(key, created);
        }
    }

    private static Map<TierDivisionType, Long> counts(GameQueueType queue, LeagueShard region) {
        String key = RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(
            GameQueueTypeUtils.canonicalQueue(queue).name(), LeagueShardUtils.leaderboardScope(region));
        Map<String, Long> stored = RedisClient.getHashLongs(key);
        if (!stored.isEmpty()) return divisions(stored);
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "leaderboard-counts:" + key,
            "leaderboard counts " + key, job -> {
                replaceCounts(queue, region);
                return null;
            });
        return Map.of();
    }

    private static void replaceCounts(GameQueueType queue, LeagueShard region) {
        Map<String, Long> values = new HashMap<>();
        for (Map.Entry<TierDivisionType, Long> entry : MongoDB.findCompetitiveRankCounts(queue, region).entrySet())
            values.put(entry.getKey().name(), entry.getValue());
        RedisClient.replaceHash(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(
            GameQueueTypeUtils.canonicalQueue(queue).name(), LeagueShardUtils.leaderboardScope(region)), values);
    }

    static Long absoluteRanking(Map<TierDivisionType, Long> counts, TierDivisionType tier, Long withinDivision) {
        if (counts == null || withinDivision == null || !counts.containsKey(tier)) return null;
        long higher = 0;
        for (TierDivisionType division : TierDivisionUtils.descendingRankedDivisions()) {
            if (division == tier) break;
            higher += counts.getOrDefault(division, 0L);
        }
        return higher + withinDivision + 1;
    }

    private static Map<TierDivisionType, Long> divisions(Map<String, Long> values) {
        Map<TierDivisionType, Long> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            try { result.put(TierDivisionType.valueOf(entry.getKey()), entry.getValue()); }
            catch (RuntimeException ignored) {}
        }
        return result;
    }

    private static byte[] member(String puuid, GameQueueType queue) {
        Binary id = MongoDB.competitiveId(puuid, GameQueueTypeUtils.canonicalQueue(queue).name());
        return id.getData();
    }

    private record RankTarget(
        String puuid,
        GameQueueType queue,
        Rank rank,
        Map<TierDivisionType, Long> globalCounts,
        Map<TierDivisionType, Long> regionalCounts
    ) {}

    private record RankSegment(GameQueueType queue, TierDivisionType tier, LeagueShard region) {}
}
