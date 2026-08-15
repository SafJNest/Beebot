package com.safjnest.lol.service;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchOrder;
import com.safjnest.lol.model.match.MatchPage;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType;
import no.stelar7.api.r4j.impl.lol.builders.matchv5.match.MatchListBuilder;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

public final class MatchService {

    private static final int MATCH_LIST_BATCH_SIZE = 100;

    private static final TypeReference<List<String>> MATCH_IDS_TYPE = new TypeReference<List<String>>() {};

    private static final no.stelar7.api.r4j.impl.R4J RIOT_API = com.safjnest.lol.LeagueHandler.getRiotApi();

    private MatchService() {
    }

    public static Match find(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return null;

        Match cached = RedisClient.get(RedisKey.MATCH_DETAIL.of(shard.name(), gameId), Match.class);
        if (cached != null) {
            cached.restoreEvents();
            return cached;
        }

        Match stored = MongoDB.findMatch(gameId);
        if (stored != null) {
            stored.restoreEvents();
            RedisClient.set(RedisKey.MATCH_DETAIL, stored, shard.name(), gameId);
        }
        return stored;
    }

    public static CompletableFuture<Match> getAsync(String gameId, LeagueShard shard) {
        Match saved = find(gameId, shard);
        return saved != null
            ? CompletableFuture.completedFuture(saved)
            : getRiotMatchAsync(gameId, shard).thenApplyAsync(source -> {
                if (source == null) return null;
                return Tracker.queueMatch(source);
            });
    }

    public static Match get(String gameId, LeagueShard shard) {
        try {
            return getAsync(gameId, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static LOLMatch getRiotMatch(String gameId, LeagueShard shard) {
        try {
            return getRiotMatchAsync(gameId, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static CompletableFuture<LOLMatch> getRiotMatchAsync(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return CompletableFuture.completedFuture(null);

        LOLMatch cached = cacheRiotMatch(gameId, shard);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        RegionShard region = shard.toRegionShard();
        return R4JQueue.submit(shard, "match", gameId, () -> {
            LOLMatch match = RIOT_API.getLoLAPI().getMatchAPI().getMatch(region, gameId);
            if (match != null) RedisClient.set(RedisKey.MATCH, match, region.name(), gameId);
            return match;
        });
    }

    public static void cacheRiotMatch(LOLMatch match) {
        if (match == null || match.getPlatform() == null) return;

        String gameId = match.getPlatform().name() + "_" + match.getGameId();
        RedisClient.set(RedisKey.MATCH, match, match.getPlatform().toRegionShard().name(), gameId);
        RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(match.getPlatform().name(), gameId));
    }

    public static void deleteRiotMatch(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return;
        RedisClient.delete(RedisKey.MATCH.of(shard.toRegionShard().name(), gameId));
    }

    public static ApiResult<Match> getDetail(String gameId, LeagueShard shard) {
        Match match = find(gameId, shard);
        if (match != null) {
            RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(shard.name(), gameId));
            return ApiResult.ready(match);
        }

        String notFound = RedisClient.get(RedisKey.MATCH_NOT_FOUND.of(shard.name(), gameId));
        if ("1".equals(notFound)) return ApiResult.notFound();

        Tracker.enqueueMatchLookup(shard, gameId);
        return ApiResult.pending();
    }

    public static MatchPage getPage(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue,
            int offset,
            int limit,
            MatchOrder order) {
        if (!valid(puuid, shard) || offset < 0 || limit < 1 || order == null) {
            return new MatchPage(List.of(), limit, offset, 0, false);
        }

        long total = MongoDB.countMatches(puuid, shard, timeStart, timeEnd, queue);
        List<MatchResult> items = MongoDB.findMatchResults(
            puuid,
            shard,
            timeStart,
            timeEnd,
            queue,
            offset,
            limit + 1,
            order.ascending()
        );
        boolean hasMore = items.size() > limit;
        if (hasMore) items = new ArrayList<>(items.subList(0, limit));
        return new MatchPage(items, limit, offset, total, hasMore);
    }

    public static void invalidate(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return;
        RedisClient.delete(RedisKey.MATCH_DETAIL.of(shard.name(), gameId));
        RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(shard.name(), gameId));
    }

    public static List<String> getRecentIds(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            GameQueueType queue,
            int index) {
        if (summoner == null || index < 0) return List.of();

        int batchIndex = index / MATCH_LIST_BATCH_SIZE * MATCH_LIST_BATCH_SIZE;
        List<String> values = getIds(summoner, queue, batchIndex, MATCH_LIST_BATCH_SIZE, 0, null);
        int batchOffset = index - batchIndex;
        return batchOffset >= values.size() ? List.of() : values.subList(batchOffset, values.size());
    }

    public static List<String> getIds(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            GameQueueType queue,
            int index,
            int count,
            long startTime,
            MatchlistMatchType type) {
        if (summoner == null || index < 0 || count < 0 || startTime < 0) return List.of();

        int requestedCount = count == 0 ? MATCH_LIST_BATCH_SIZE : count;
        String requestKey = matchListRequestKey(queue, requestedCount, startTime, type);
        String cacheKey = RedisKey.MATCH_LIST.of(summoner.getPlatform().name(), summoner.getPUUID(), requestKey, index);
        List<String> cached = RedisClient.get(cacheKey, MATCH_IDS_TYPE);
        if (cached != null) return cached;

        try {
            String id = summoner.getPUUID() + ":" + requestKey + ":" + index;
            return R4JQueue.<List<String>>submit(summoner.getPlatform(), "match-list", id, () -> {
                MatchListBuilder builder = matchListBuilder(summoner, queue, index, requestedCount, startTime, type);
                List<String> values = builder.get();
                if (values == null) return List.of();
                RedisClient.set(RedisKey.MATCH_LIST, values,
                    summoner.getPlatform().name(), summoner.getPUUID(), requestKey, index);
                return values;
            }).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static List<String> getSeasonPuuids(LeagueShard shard, long seasonStart, long seasonEnd) {
        return MongoDB.findSeasonSummonerPuuids(shard, seasonStart, seasonEnd);
    }

    public static List<QueryRecord> getSummonerData(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_DATA.of(puuid, shard.name());
        List<QueryRecord> cached = RedisClient.get(key, new TypeReference<List<QueryRecord>>() {});
        if (cached != null) return cached;

        List<QueryRecord> result = MongoDB.findSummonerData(
            puuid,
            shard,
            0,
            Long.MAX_VALUE,
            GameQueueType.TEAM_BUILDER_RANKED_SOLO
        );
        RedisClient.set(RedisKey.SUMMONER_DATA, result, puuid, shard.name());
        return result;
    }

    // ============================================================================

    private static MatchListBuilder matchListBuilder(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            GameQueueType queue,
            int index,
            int count,
            long startTime,
            MatchlistMatchType type) {
        MatchListBuilder builder = summoner.getLeagueGames().withBeginIndex(index);
        if (count > 0) builder = builder.withCount(count);
        if (startTime > 0) builder = builder.withStartTime(startTime);
        return type != null ? builder.withType(type) : builder.withQueue(queue);
    }

    private static String matchListRequestKey(
            GameQueueType queue,
            int count,
            long startTime,
            MatchlistMatchType type) {
        return "queue=" + (queue == null ? "null" : queue.name())
            + ":count=" + count
            + ":startTime=" + startTime
            + ":type=" + (type == null ? "null" : type.name());
    }

    private static LOLMatch cacheRiotMatch(String gameId, LeagueShard shard) {
        return RedisClient.get(
            RedisKey.MATCH.of(shard.toRegionShard().name(), gameId),
            LOLMatch.class
        );
    }

    private static void save(Match match) {
        if (!valid(match, match.gameId, match.leagueShard)) return;

        if (!MongoDB.upsertMatch(match.gameId, match)) return;
        RedisClient.set(RedisKey.MATCH_DETAIL, match, match.leagueShard.name(), match.gameId);
        deleteRiotMatch(match.gameId, match.leagueShard);
    }
}
