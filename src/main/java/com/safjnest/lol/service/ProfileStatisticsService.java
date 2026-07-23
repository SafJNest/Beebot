package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.mongo.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileStatisticsService {

    private static final int TTL_PROFILE_STATISTICS = 60 * 60;
    private static final int TTL_PROFILE_RECENT_MATCHES = 60 * 5;
    private static final TypeReference<List<MatchResult>> RECENT_MATCHES_TYPE = new TypeReference<>() {};

    public ProfileStatistics get(String puuid, Filter filter) {
        if (puuid == null || filter == null) return null;
        ProfileStatistics statistics = RedisClient.get(redisKey(puuid, filter), ProfileStatistics.class);
        if (statistics != null) return statistics;
        statistics = MongoDB.findProfileStatistics(puuid, filter);
        if (statistics != null) cache(puuid, filter, statistics);
        return statistics;
    }

    public ProfileStatistics get(String puuid, SeasonUtils.SeasonRange season) {
        return season == null ? null : get(puuid, Filter.summoner(season.start(), season.end()));
    }

    public Map<String, ProfileStatistics> getByPuuid(List<String> puuids, Filter filter) {
        Map<String, ProfileStatistics> result = new HashMap<>();
        if (filter == null || puuids == null || puuids.isEmpty()) return result;
        List<String> missing = new ArrayList<>();
        List<String> keys = new ArrayList<>(puuids.size());
        Map<String, String> keysByRedisKey = new HashMap<>();
        for (String puuid : puuids) {
            String key = redisKey(puuid, filter);
            keys.add(key);
            keysByRedisKey.put(key, puuid);
        }
        for (Map.Entry<String, ProfileStatistics> entry : RedisClient.get(keys, ProfileStatistics.class).entrySet()) {
            String puuid = keysByRedisKey.get(entry.getKey());
            if (puuid != null) result.put(puuid, entry.getValue());
        }
        for (String puuid : puuids) if (!result.containsKey(puuid)) missing.add(puuid);
        if (!missing.isEmpty()) {
            Map<String, ProfileStatistics> stored = MongoDB.findProfileStatistics(missing, filter);
            for (Map.Entry<String, ProfileStatistics> entry : stored.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
                cache(entry.getKey(), filter, entry.getValue());
            }
        }
        return result;
    }

    public Map<String, ProfileStatistics> getByPuuid(List<String> puuids, SeasonUtils.SeasonRange season) {
        return season == null ? Map.of() : getByPuuid(puuids, Filter.summoner(season.start(), season.end()));
    }

    public List<MatchResult> getRecentMatches(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || filter == null) return List.of();
        String key = recentMatchesKey(puuid, filter);
        List<MatchResult> cached = RedisClient.get(key, RECENT_MATCHES_TYPE);
        if (cached != null) return cached;
        List<MatchResult> result = MongoDB.findProfileRecentMatches(puuid, shard, filter, 5);
        RedisClient.set(key, result, TTL_PROFILE_RECENT_MATCHES);
        return result;
    }

    public boolean refresh(String puuid, LeagueShard shard, Filter filter, boolean rebuild) {
        if (puuid == null || shard == null || filter == null) return false;
        ProfileStatistics statistics = rebuild ? null : get(puuid, filter);
        if (statistics == null) statistics = new ProfileStatistics(filter.timeStart());

        long afterTime = rebuild || statistics.timeEnd == filter.timeStart() ? 0 : statistics.timeEnd + 1;
        long untilTime = currentEnd(filter);
        for (Match match : MongoDB.findProfileStatisticsMatches(puuid, shard, filter, afterTime, untilTime))
            statistics.add(match, puuid, filter);

        statistics.lastUpdate = System.currentTimeMillis();
        boolean saved = MongoDB.upsertProfileStatistics(puuid, filter, statistics);
        if (saved) {
            cache(puuid, filter, statistics);
            RedisClient.delete(recentMatchesKey(puuid, filter));
        }
        return saved;
    }

    public boolean refresh(String puuid, LeagueShard shard, SeasonUtils.SeasonRange season, boolean rebuild) {
        return season != null && refresh(puuid, shard, Filter.summoner(season.start(), season.end()), rebuild);
    }

    // ============================================================================

    private void cache(String puuid, Filter filter, ProfileStatistics statistics) {
        RedisClient.set(redisKey(puuid, filter), statistics, TTL_PROFILE_STATISTICS);
    }

    private static long currentEnd(Filter filter) {
        return filter.timeEnd() == 0 ? System.currentTimeMillis() : Math.min(System.currentTimeMillis(), filter.timeEnd());
    }

    private static String redisKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_STATISTICS.of(puuid, filter.toSummonerKey());
    }

    private static String recentMatchesKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_RECENT_MATCHES.of(puuid, filter.toSummonerKey());
    }
}
