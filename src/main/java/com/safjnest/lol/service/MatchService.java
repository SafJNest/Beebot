package com.safjnest.lol.service;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.match.Match;
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
                Match match = Match.fromR4J(source);
                if (match != null) save(match);
                return match;
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

    public static void invalidate(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return;
        RedisClient.delete(RedisKey.MATCH_DETAIL.of(shard.name(), gameId));
        RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(shard.name(), gameId));
    }

    public static List<String> getRecentIds(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            GameQueueType queue,
            int index) {
        if (summoner == null) return new ArrayList<>();

        String queueKey = queue != null ? queue.name() : "null";
        String key = RedisKey.MATCH_LIST.of(summoner.getPlatform().name(), summoner.getPUUID(), queueKey, index);
        List<String> cached = RedisClient.get(key, MATCH_IDS_TYPE);
        if (cached != null) return cached;

        try {
            String id = summoner.getPUUID() + ":" + queueKey + ":" + index + ":0:0:null";
            return R4JQueue.<List<String>>submit(summoner.getPlatform(), "match-list", id, () -> {
                List<String> matchList = summoner.getLeagueGames().withQueue(queue).withBeginIndex(index).get();
                List<String> result = matchList == null ? new ArrayList<>() : matchList;
                if (matchList != null) RedisClient.set(RedisKey.MATCH_LIST, result,
                    summoner.getPlatform().name(), summoner.getPUUID(), queueKey, index);
                return result;
            }).join();
        } catch (CompletionException exception) {
            return new ArrayList<>();
        }
    }

    public static List<String> getIds(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            GameQueueType queue,
            int index,
            int count,
            long startTime,
            MatchlistMatchType type) {
        if (summoner == null || index < 0 || count < 0 || startTime < 0) return List.of();

        String queueKey = queue == null ? "null" : queue.name();
        String typeKey = type == null ? "null" : type.name();
        String id = summoner.getPUUID() + ":" + queueKey + ":" + index + ":" + count + ":" + startTime + ":" + typeKey;
        try {
            return R4JQueue.<List<String>>submit(summoner.getPlatform(), "match-list", id, () -> {
                MatchListBuilder builder = summoner.getLeagueGames().withBeginIndex(index);
                if (count > 0) builder.withCount(count);
                if (startTime > 0) builder.withStartTime(startTime);
                if (type != null) builder.withType(type);
                else builder.withQueue(queue);
                List<String> values = builder.get();
                return values == null ? List.of() : values;
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
