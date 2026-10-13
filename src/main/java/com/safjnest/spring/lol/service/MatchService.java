package com.safjnest.spring.lol.service;

import com.safjnest.s4b.RiotGateway;
import com.safjnest.spring.lol.tracker.Tracker;
import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.spring.lol.LeagueHandler;
import com.R4B.api.dto.ApiResult;
import com.R4B.api.dto.Filter;
import com.R4B.api.dto.match.Match;
import com.R4B.api.dto.match.MatchPage;
import com.R4B.api.dto.match.MatchResult;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.match.RankHistory;
import com.R4B.api.dto.match.RankHistoryMatch;
import com.R4B.api.dto.match.RankHistoryMetadata;
import com.R4B.api.dto.match.RankHistoryQuery;
import com.R4B.api.dto.match.RankProgress;
import com.R4B.api.dto.riot.RiotMatchData;
import com.R4B.api.dto.riot.RiotTimelineData;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.lol.*;
import com.safjnest.spring.lol.queue.QueueHandler;
import com.hive.job.ResultType;
import com.hive.job.Job;
import com.hive.job.JobPriority;
import com.safjnest.s4b.scheduler.RiotScheduler;
import com.safjnest.spring.lol.scheduler.SyncScheduler;
import com.safjnest.spring.lol.utils.MatchUtils;
import com.safjnest.spring.lol.utils.RankProgressUtils;
import com.safjnest.spring.lol.utils.SeasonUtils;
import com.safjnest.spring.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.JsonCodec;
import com.safjnest.utils.log.BotLogger;

public final class MatchService {

    static final int MATCH_LIST_BATCH_SIZE = 100;

    private static final TypeReference<List<String>> MATCH_IDS_TYPE = new TypeReference<List<String>>() {};
    private static final TypeReference<List<RankHistoryMatch>> RANK_HISTORY_TYPE = new TypeReference<>() {};

    private MatchService() {
    }

    public static Match find(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return null;

        Match cached = RedisClient.get(RedisKey.MATCH_DETAIL.of(LeagueShard.cacheRegion(shard), shard.name(), gameId), Match.class);
        if (cached != null) {
            return cached;
        }

        Match stored = MongoDB.findMatch(gameId);
        if (stored != null) {
            RedisClient.set(RedisKey.MATCH_DETAIL, stored, LeagueShard.cacheRegion(shard), shard.name(), gameId);
        }
        return stored;
    }

    public static CompletableFuture<Match> getAsync(String gameId, LeagueShard shard) {
        Match saved = find(gameId, shard);
        return saved != null
            ? CompletableFuture.completedFuture(saved)
            : QueueHandler.immediate(
                SyncScheduler.class,
                shard,
                "match:" + gameId,
                "match analysis id=" + gameId, Match.class,
                ignored -> insert(fetch(gameId, shard))
            );
    }

    public static Match get(String gameId, LeagueShard shard) {
        try {
            return getAsync(gameId, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static RiotMatchData fetch(String gameId, LeagueShard shard) {
        try {
            return fetchAsync(gameId, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static CompletableFuture<RiotMatchData> fetchAsync(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return CompletableFuture.completedFuture(null);

        RiotMatchData cached = getCachedR4JMatch(gameId, shard);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        RegionShard region = shard.toRegionShard();
        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":match:" + gameId,
            "match id=" + gameId, RiotMatchData.class, ignored -> {
            RiotMatchData match = RiotGateway.match(region, gameId);
            if (match != null) RedisClient.set(RedisKey.R4J_MATCH, match, region.name(), gameId);
            return match;
        });
    }

    public static Match insert(RiotMatchData source) {
        if (source == null || source.getPlatform() == null) return null;
        String fullGameId = MatchUtils.fullGameId(source);
        cacheR4JMatch(source);
        if (MatchUtils.isRemake(source)) {
            invalidateR4JMatch(fullGameId, source.getPlatform());
            return null;
        }

        Match match = Tracker.fromR4J(source);
        if (match == null) return null;
        localRanking(match);
        boolean inserted = MongoDB.insertMatch(match);
        if (source.getParticipants() == null) return null;
        for (var participant : source.getParticipants()) {
            if (!MongoDB.upsertSummoner(participant, source.getPlatform())) return null;
        }
        if (inserted) invalidate(match.gameId, match.leagueShard);
        invalidateR4JMatch(fullGameId, source.getPlatform());
        return inserted ? match : find(fullGameId, source.getPlatform());
    }

    public static CompletableFuture<Match> insertAsync(RiotMatchData source, JobPriority priority) {
        if (source == null || source.getPlatform() == null) return CompletableFuture.completedFuture(null);
        String gameId = MatchUtils.fullGameId(source);
        return switch (priority == null ? JobPriority.NORMAL : priority) {
            case IMMEDIATE -> QueueHandler.immediate(SyncScheduler.class, source.getPlatform(), "match:" + gameId,
                "match insert id=" + gameId, Match.class, ignored -> insert(source));
            case NORMAL -> QueueHandler.normal(SyncScheduler.class, source.getPlatform(), "match:" + gameId,
                "match insert id=" + gameId, Match.class, ignored -> insert(source));
            case BACKGROUND -> QueueHandler.background(SyncScheduler.class, source.getPlatform(), "match:" + gameId,
                "match insert id=" + gameId, Match.class, ignored -> insert(source));
        };
    }

    public static void upsertRankProgress(List<Match> matches, LeagueShard shard) {
        if (matches == null || matches.isEmpty() || shard == null) return;
        Map<String, List<String>> gameIdsByPuuid = new java.util.LinkedHashMap<>();
        for (Match match : matches) {
            if (match == null || match.gameId == null || match.participants == null) continue;
            for (Participant participant : match.participants) {
                if (participant == null || participant.puuid == null || participant.puuid.isBlank()) continue;
                gameIdsByPuuid.computeIfAbsent(participant.puuid, ignored -> new ArrayList<>()).add(match.gameId);
            }
        }
        if (gameIdsByPuuid.isEmpty()) return;

        Map<String, Rank> ranks = MongoDB.findSoloRanksByPuuid(new ArrayList<>(gameIdsByPuuid.keySet()), shard);
        for (Map.Entry<String, List<String>> entry : gameIdsByPuuid.entrySet()) {
            String puuid = entry.getKey();
            Rank rank = ranks.get(puuid);
            if (rank != null) {
                upsertRankProgress(entry.getValue(), puuid, RankProgressUtils.snapshot(rank), shard);
                continue;
            }
            QueueHandler.whenComplete(RankService.refreshBackgroundAsync(puuid, shard), (refreshed, failure) -> {
                if (failure != null) return;
                upsertRankProgress(entry.getValue(), puuid, RankProgressUtils.snapshot(soloRank(refreshed)), shard);
            });
        }
    }

    public static void cacheR4JMatch(RiotMatchData match) {
        if (match == null || match.getPlatform() == null) return;

        String gameId = match.getPlatform().name() + "_" + match.getGameId();
        RedisClient.set(RedisKey.R4J_MATCH, match, match.getPlatform().toRegionShard().name(), gameId);
    }

    public static RiotTimelineData getTimeline(RiotMatchData match) {
        if (match == null || match.getPlatform() == null) return null;
        return getTimeline(MatchUtils.fullGameId(match), match.getPlatform());
    }

    public static RiotTimelineData getTimeline(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return null;
        String region = shard.toRegionShard().name();
        RiotTimelineData cached = RedisClient.get(RedisKey.R4J_TIMELINE.of(region, gameId), RiotTimelineData.class);
        if (cached != null) return cached;

        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":timeline:" + gameId,
            "timeline id=" + gameId, RiotTimelineData.class, ignored -> {
                RiotTimelineData timeline = RiotGateway.timeline(shard.toRegionShard(), gameId);
                if (timeline != null) RedisClient.set(RedisKey.R4J_TIMELINE, timeline, region, gameId);
                return timeline;
            }).join();
    }

    public static void clearTimelineCache(RiotMatchData match) {
        if (match == null || match.getPlatform() == null) return;
        clearTimelineCache(MatchUtils.fullGameId(match), match.getPlatform());
    }

    public static void clearTimelineCache(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return;
        CacheInvalidationService.clearRedis(
            RedisKey.R4J_TIMELINE, shard.toRegionShard().name(), gameId);
    }

    public static void invalidateR4JMatch(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return;
        CacheInvalidationService.clearRedis(RedisKey.R4J_MATCH, shard.toRegionShard().name(), gameId);
    }

    public static ApiResult<Match> getDetail(String gameId, LeagueShard shard) {
        Match match = find(gameId, shard);
        if (match != null) return ApiResult.ready(match);
        queue(shard, gameId);
        return ApiResult.pending();
    }

    public static void queue(LeagueShard shard, String gameId) {
        if (!valid(gameId, shard)) return;
        QueueHandler.background(SyncScheduler.class, shard, "match:" + gameId,
            "match fetch id=" + gameId, Match.class, task -> fetchAndInsert(task, shard, gameId));
    }

    public static void queueRecentMatches(com.R4B.api.dto.summoner.Summoner summoner, int limit) {
        if (summoner == null || summoner.getPUUID() == null || summoner.getPlatform() == null || limit < 1) return;
        String key = "recent-matches:" + summoner.getPlatform().name() + ':' + summoner.getPUUID();
        QueueHandler.background(SyncScheduler.class, summoner.getPlatform(), key,
            "recent matches puuid=" + summoner.getPUUID(), Void.class, task -> {
                QueueHandler.retain(task);
                getMatchlistAsync(summoner, null, 0, limit, 0, null).whenComplete((matchIds, failure) ->
                    QueueHandler.resume(task, () -> queueMissingMatches(summoner.getPlatform(), matchIds, failure)));
                return null;
            });
    }

    public static void importHistory(com.R4B.api.dto.summoner.Summoner summoner, GameQueueType queue) {
        if (summoner == null || summoner.getPlatform() == null) return;
        try {
            List<String> matchIds = new ArrayList<>();
            List<String> page;
            do {
                page = getMatchlist(summoner, queue, matchIds.size(), MATCH_LIST_BATCH_SIZE, 0, null);
                if (page == null) page = List.of();
                matchIds.addAll(page);
                Thread.sleep(350);
            } while (!page.isEmpty());

            int total = matchIds.size();
            java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(0);
            for (String matchId : matchIds) {
                if (LeagueHandler.isMatchDBCached(matchId)) continue;

                QueueHandler.whenComplete(fetchAsync(matchId, summoner.getPlatform()), (match, failure) -> {
                    if (failure != null) {
                        BotLogger.error("Match fetch failed: " + matchId + " message=" + failure.getMessage());
                        return;
                    }
                    if (match == null) return;
                    try {
                        Match result = insert(match);
                        BotLogger.info("[" + done.incrementAndGet() + "/" + total + "] " + match.getGameId() + " - "
                            + match.getPlatform() + " - " + match.getQueue() + " rank=" + (result == null ? "null" : result.rank));
                    } catch (Throwable throwable) {
                        BotLogger.error("Import insert failed: " + matchId + " " + throwable);
                    }
                });
            }
        } catch (Exception exception) {
            BotLogger.error("Match history import failed for puuid=" + summoner.getPUUID() + " message=" + exception.getMessage());
        }
    }

    public static MatchPage getPage(
            String puuid,
            Filter filter,
            int offset,
            int limit,
            MatchOrder order) {
        if (filter == null || !valid(puuid, filter.region()) || offset < 0 || limit < 1 || order == null) {
            return new MatchPage(List.of(), limit, offset, 0, false);
        }

        long total = MongoDB.countMatches(puuid, filter);
        List<MatchResult> items = MongoDB.findMatchResults(
            puuid,
            filter,
            offset,
            limit + 1,
            order.ascending()
        );
        boolean hasMore = items.size() > limit;
        if (hasMore) items = new ArrayList<>(items.subList(0, limit));
        return new MatchPage(items, limit, offset, total, hasMore);
    }

    public static RankHistory getRankHistory(String puuid, LeagueShard shard, RankHistoryQuery query) {
        if (!valid(puuid, shard) || query == null || !isRankHistoryQueue(query.queue()) || query.order() == null) {
            return new RankHistory(List.of(), 0, null);
        }
        RankHistoryPeriod period = rankHistoryPeriod(query);
        Filter filter = Filter.summoner(period.timeStart(), period.timeEnd()).setQueue(query.queue());
        RankHistoryMetadata metadata = new RankHistoryMetadata(
            query.view() == null ? null : query.view().value(),
            period.season() == null ? null : period.season().year(),
            query.patch(),
            query.timeStart() == 0 ? null : query.timeStart(),
            query.timeEnd() == 0 ? null : query.timeEnd(),
            filter
        );
        if (period.timeEnd() < period.timeStart()) return new RankHistory(List.of(), 0, metadata);

        List<RankHistoryMatch> source = rankHistorySource(puuid, shard, period.seasons());
        List<RankHistoryMatch> items = new ArrayList<>();
        for (RankHistoryMatch match : source) {
            if (matchesRankHistoryFilter(match, period.timeStart(), period.timeEnd(), query.queue(), query.patch())) items.add(match);
        }
        items.sort(rankHistoryOrder(query.order()));
        return new RankHistory(items, items.size(), metadata);
    }

    public static void invalidate(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return;
        CacheInvalidationService.clearRedis(
            RedisKey.MATCH_DETAIL, LeagueShard.cacheRegion(shard), shard.name(), gameId);
    }

    public static void invalidate(Match match) {
        if (match == null || !valid(match.gameId, match.leagueShard)) return;
        invalidate(match.gameId, match.leagueShard);
        if (match.participants == null) return;
        for (Participant participant : match.participants) {
            if (participant == null || participant.puuid == null || participant.puuid.isBlank()) continue;
            CacheInvalidationService.clearRedis(
                RedisKey.SUMMONER_DATA,
                LeagueShard.cacheRegion(match.leagueShard), match.leagueShard.name(), participant.puuid);
        }
    }

    public static List<String> getRecentIds(
            com.R4B.api.dto.summoner.Summoner summoner,
            GameQueueType queue,
            int index) {
        if (summoner == null || index < 0) return List.of();

        int batchIndex = index / MATCH_LIST_BATCH_SIZE * MATCH_LIST_BATCH_SIZE;
        List<String> values = getMatchlist(summoner, queue, batchIndex, MATCH_LIST_BATCH_SIZE, 0, null);
        int batchOffset = index - batchIndex;
        return batchOffset >= values.size() ? List.of() : values.subList(batchOffset, values.size());
    }

    public static void invalidateMatchlist(
            com.R4B.api.dto.summoner.Summoner summoner,
            GameQueueType queue,
            int index) {
        if (summoner == null || index < 0) return;

        int batchIndex = index / MATCH_LIST_BATCH_SIZE * MATCH_LIST_BATCH_SIZE;
        CacheInvalidationService.clearMatchHistory(summoner, queue, batchIndex, MATCH_LIST_BATCH_SIZE, 0, null);
    }

    public static List<String> getMatchlist(
            com.R4B.api.dto.summoner.Summoner summoner,
            GameQueueType queue,
            int index,
            int count,
            long startTime,
            MatchlistMatchType type) {
        if (summoner == null || index < 0 || count < 0 || startTime < 0) return List.of();

        int requestedCount = count == 0 ? MATCH_LIST_BATCH_SIZE : count;
        String requestKey = matchListRequestKey(queue, requestedCount, startTime, type);
        String cacheKey = RedisKey.R4J_MATCH_LIST.of(summoner.getPlatform().name(), summoner.getPUUID(), requestKey, index);
        List<String> cached = RedisClient.get(cacheKey, MATCH_IDS_TYPE);
        if (cached != null) return cached;

        try {
            return getMatchlistAsync(summoner, queue, index, count, startTime, type).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static CompletableFuture<List<String>> getMatchlistAsync(
            com.R4B.api.dto.summoner.Summoner summoner,
            GameQueueType queue,
            int index,
            int count,
            long startTime,
            MatchlistMatchType type) {
        if (summoner == null || index < 0 || count < 0 || startTime < 0) return CompletableFuture.completedFuture(List.of());

        int requestedCount = count == 0 ? MATCH_LIST_BATCH_SIZE : count;
        String requestKey = matchListRequestKey(queue, requestedCount, startTime, type);
        String cacheKey = RedisKey.R4J_MATCH_LIST.of(summoner.getPlatform().name(), summoner.getPUUID(), requestKey, index);
        List<String> cached = RedisClient.get(cacheKey, MATCH_IDS_TYPE);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        String id = summoner.getPUUID() + ":" + requestKey + ":" + index;
        return QueueHandler.immediate(RiotScheduler.class, summoner.getPlatform(), summoner.getPlatform().name() + ":match-list:" + id,
            "match list puuid=" + summoner.getPUUID(), ResultType.list(String.class), ignored -> {
                List<String> values = RiotGateway.matchIds(summoner, queue, index, requestedCount, startTime, type);
                if (values == null) return List.of();
                RedisClient.set(RedisKey.R4J_MATCH_LIST, values,
                    summoner.getPlatform().name(), summoner.getPUUID(), requestKey, index);
                return values;
            });
    }

    public static List<String> getSeasonPuuids(LeagueShard shard, long seasonStart, long seasonEnd) {
        return MongoDB.findSeasonSummonerPuuids(shard, seasonStart, seasonEnd);
    }

    public static List<QueryRecord> getSummonerData(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_DATA.of(LeagueShard.cacheRegion(shard), shard.name(), puuid);
        List<QueryRecord> cached = RedisClient.get(key, new TypeReference<List<QueryRecord>>() {});
        if (cached != null) return cached;

        List<QueryRecord> result = MongoDB.findSummonerData(
            puuid,
            shard,
            0,
            Long.MAX_VALUE,
            GameQueueType.TEAM_BUILDER_RANKED_SOLO
        );
        RedisClient.set(RedisKey.SUMMONER_DATA, result, LeagueShard.cacheRegion(shard), shard.name(), puuid);
        return result;
    }

    public static String matchListRequestKey(
            GameQueueType queue,
            int count,
            long startTime,
            MatchlistMatchType type) {
        return "queue=" + (queue == null ? "null" : queue.name())
            + ":count=" + count
            + ":startTime=" + startTime
            + ":type=" + (type == null ? "null" : type.name());
    }

    // ============================================================================

    private static Rank soloRank(Map<GameQueueType, Rank> ranks) {
        return ranks == null ? null : ranks.get(GameQueueType.RANKED_SOLO_5X5);
    }

    private static void localRanking(Match match) {
        if (match == null || match.rank != null || match.leagueShard == null || match.participants == null || match.queue == null) return;
        GameQueueType queue = GameQueueType.rankedCanonicalQueue(match.queue);
        List<TierDivisionType> tiers = new ArrayList<>();
        for (Participant participant : match.participants) {
            if (participant == null || participant.puuid == null || participant.puuid.isBlank()) continue;
            Map<GameQueueType, Rank> ranks = MongoDB.findRanks(participant.puuid, match.leagueShard);
            if (ranks == null) continue;
            Rank rank = ranks.get(queue);
            if (rank == null) rank = Rank.unranked();
            participant.rankProgress = RankProgressUtils.snapshot(rank);
            if (rank.tier() != null) tiers.add(rank.tier());
        }
        if (!tiers.isEmpty()) match.setRank(TierDivisionType.getAverageRank(tiers));
    }

    private static void upsertRankProgress(List<String> gameIds, String puuid, RankProgress progress, LeagueShard shard) {
        if (!RankProgressUtils.hasCurrentSnapshot(progress) || gameIds == null) return;
        for (String gameId : gameIds) {
            if (MongoDB.updateUntrackedParticipantRankProgress(gameId, puuid, progress)) invalidate(gameId, shard);
        }
    }

    private static Match fetchAndInsert(Job<?> task, LeagueShard shard, String gameId) {
        task.phase("FETCH");
        task.trackItem(gameId);
        Match match = insert(fetch(gameId, shard));
        if (match != null) task.done(gameId);
        else task.missing(gameId);
        return match;
    }

    private static void queueMissingMatches(LeagueShard shard, List<String> matchIds, Throwable failure) {
        if (failure != null) throw new IllegalStateException("Recent matches lookup failed", failure);
        if (matchIds == null) return;
        for (String matchId : matchIds) {
            if (MongoDB.hasMatch(matchId)) continue;
            queue(MatchUtils.matchShard(matchId, shard), matchId);
        }
    }

    private static List<RankHistoryMatch> rankHistorySource(
            String puuid,
            LeagueShard shard,
            List<SeasonUtils.SeasonRange> seasons) {
        List<RankHistoryMatch> result = new ArrayList<>();
        for (SeasonUtils.SeasonRange season : seasons) result.addAll(rankHistorySource(puuid, shard, season));
        return result;
    }

    private static List<RankHistoryMatch> rankHistorySource(
            String puuid,
            LeagueShard shard,
            SeasonUtils.SeasonRange season) {
        String cacheRegion = LeagueShard.cacheRegion(shard);
        String key = RedisKey.SUMMONER_RANK_HISTORY.of(cacheRegion, shard.name(), puuid, season.season());
        List<RankHistoryMatch> cached = RedisClient.get(key, RANK_HISTORY_TYPE);
        if (cached != null) return cached;

        List<RankHistoryMatch> source = MongoDB.findRankHistoryMatches(puuid, shard, season.start(), season.end());
        RedisClient.setCached(RedisKey.SUMMONER_RANK_HISTORY, JsonCodec.toJson(source),
            cacheRegion, shard.name(), puuid, season.season());
        return source;
    }

    private static boolean matchesRankHistoryFilter(
            RankHistoryMatch match,
            long timeStart,
            long timeEnd,
            GameQueueType queue,
            String patch) {
        return match != null && GameQueueType.canonicalQueue(queue) == GameQueueType.canonicalQueue(match.queue())
            && match.timeStart() >= timeStart && match.timeStart() <= timeEnd
            && (patch == null || patch.equals(match.patch()));
    }

    private static Comparator<RankHistoryMatch> rankHistoryOrder(MatchOrder order) {
        Comparator<RankHistoryMatch> comparator = Comparator.comparingLong(RankHistoryMatch::timeStart)
            .thenComparing(RankHistoryMatch::gameId);
        return order.ascending() ? comparator : comparator.reversed();
    }

    private static boolean isRankHistoryQueue(GameQueueType queue) {
        GameQueueType canonical = GameQueueType.canonicalQueue(queue);
        return canonical == GameQueueType.RANKED_SOLO_5X5 || canonical == GameQueueType.RANKED_FLEX_SR;
    }

    private static RankHistoryPeriod rankHistoryPeriod(RankHistoryQuery query) {
        SeasonUtils.SeasonRange selected = rankHistorySeason(query);
        long now = System.currentTimeMillis();
        long start = selected.start();
        long end = selected.end();
        if (query.view() == RankHistoryView.PROFILE) {
            start = now - java.time.Duration.ofDays(10).toMillis();
            end = now;
            selected = null;
        } else if (query.timeStart() != 0) {
            start = Math.max(start, query.timeStart());
        } else if (query.timeEnd() != 0) {
            end = Math.min(end, query.timeEnd());
        }
        List<SeasonUtils.SeasonRange> seasons = selected == null
            ? SeasonUtils.getSeasonRanges(start, end)
            : List.of(selected);
        return new RankHistoryPeriod(selected, seasons, start, end);
    }

    private static SeasonUtils.SeasonRange rankHistorySeason(RankHistoryQuery query) {
        if (query.season() != null) return SeasonUtils.getSeasonRange(query.season());
        if (query.patch() != null) {
            int separator = query.patch().indexOf('.');
            int year = 2010 + Integer.parseInt(query.patch().substring(0, separator));
            SeasonUtils.SeasonRange season = SeasonUtils.getSeasonRange(year);
            if (season == null) throw new IllegalArgumentException("No configured season matches patch " + query.patch());
            return season;
        }
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        if (season == null) throw new IllegalStateException("Current season range is unavailable");
        return season;
    }

    private record RankHistoryPeriod(
        SeasonUtils.SeasonRange season,
        List<SeasonUtils.SeasonRange> seasons,
        long timeStart,
        long timeEnd
    ) {}

    private static RiotMatchData getCachedR4JMatch(String gameId, LeagueShard shard) {
        return RedisClient.get(
            RedisKey.R4J_MATCH.of(shard.toRegionShard().name(), gameId),
            RiotMatchData.class
        );
    }
}
