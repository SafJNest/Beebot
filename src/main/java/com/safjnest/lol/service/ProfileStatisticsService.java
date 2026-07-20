package com.safjnest.lol.service;

import com.safjnest.mongo.MongoDB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.lol.utils.SeasonUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileStatisticsService {

    private static final int TTL_PROFILE_STATISTICS = 60 * 60;
    public ProfileStatistics get(String puuid, SeasonUtils.SeasonRange season) {
        if (puuid == null || season == null) return null;
        ProfileStatistics statistics = loadRedis(puuid, season.start());
        if (statistics != null) return statistics;
        statistics = MongoDB.findProfileStatistics(puuid, season.start());
        if (statistics != null) cache(puuid, season.start(), statistics);
        return statistics;
    }

    public Map<String, ProfileStatistics> getByPuuid(List<String> puuids, SeasonUtils.SeasonRange season) {
        Map<String, ProfileStatistics> result = new HashMap<>();
        if (season == null || puuids == null || puuids.isEmpty()) return result;
        List<String> missing = new ArrayList<>();
        List<String> keys = new ArrayList<>(puuids.size());
        Map<String, String> keysByPuuid = new HashMap<>();
        for (String puuid : puuids) {
            String key = redisKey(puuid, season.start());
            keys.add(key);
            keysByPuuid.put(key, puuid);
        }
        for (Map.Entry<String, ProfileStatistics> entry : RedisClient.get(keys, ProfileStatistics.class).entrySet()) {
            String puuid = keysByPuuid.get(entry.getKey());
            if (puuid != null) result.put(puuid, entry.getValue());
        }
        for (String puuid : puuids) {
            if (!result.containsKey(puuid)) missing.add(puuid);
        }
        if (!missing.isEmpty()) {
            Map<String, ProfileStatistics> stored = MongoDB.findProfileStatistics(missing, season.start());
            for (Map.Entry<String, ProfileStatistics> entry : stored.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
                cache(entry.getKey(), season.start(), entry.getValue());
            }
        }
        return result;
    }

    public boolean refresh(String puuid, LeagueShard shard, SeasonUtils.SeasonRange season, boolean rebuild) {
        if (puuid == null || shard == null || season == null) return false;
        ProfileStatistics statistics = rebuild ? null : get(puuid, season);
        if (statistics == null) {
            statistics = new ProfileStatistics(season.start());
            merge(statistics, MongoDB.findMatchResults(
                    puuid, shard, season.start(), currentEnd(season), null, 0, 100));
        } else {
            merge(statistics, MongoDB.findMatchResults(
                    puuid, shard, statistics.timeEnd, currentEnd(season), null, 0, 100));
        }
        boolean saved = MongoDB.upsertProfileStatistics(puuid, season.start(), statistics);
        if (saved) cache(puuid, season.start(), statistics);
        return saved;
    }

    // ============================================================================

    private ProfileStatistics loadRedis(String puuid, long timeStart) {
        return RedisClient.get(redisKey(puuid, timeStart), ProfileStatistics.class);
    }

    private void merge(ProfileStatistics statistics, List<MatchResult> matches) {
        for (MatchResult match : matches) {
            GameQueueType queue = match.queue();
            if (queue != null) statistics.add(match, queue, match.lane());
        }
    }

    private void cache(String puuid, long timeStart, ProfileStatistics statistics) {
        RedisClient.set(redisKey(puuid, timeStart), statistics, TTL_PROFILE_STATISTICS);
    }

    private static long currentEnd(SeasonUtils.SeasonRange season) {
        return Math.min(System.currentTimeMillis(), season.end());
    }

    private static String redisKey(String puuid, long timeStart) {
        return RedisKey.PROFILE_STATISTICS.of(puuid, timeStart);
    }

}
