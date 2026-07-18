package com.safjnest.lol.service;

import com.safjnest.mongo.MongoDB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.lol.utils.SeasonUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileStatisticsService {

    private static final int TTL_PROFILE_STATISTICS = 60 * 60;
    public ProfileStatistics get(int summonerId, SeasonUtils.SeasonRange season) {
        return season == null ? null : load(summonerId, season);
    }

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
        for (String puuid : puuids) {
            ProfileStatistics statistics = loadRedis(puuid, season.start());
            if (statistics != null) result.put(puuid, statistics);
            else missing.add(puuid);
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

    public ProfileStatistics getDatabase(int summonerId, SeasonUtils.SeasonRange season) {
        return season == null ? null : loadDatabase(summonerId, season.start());
    }

    public ProfileStatistics getRedis(int summonerId, SeasonUtils.SeasonRange season) {
        return season == null ? null : loadRedis(summonerId, season.start());
    }

    public Map<Integer, ProfileStatistics> get(List<Integer> summonerIds, SeasonUtils.SeasonRange season) {
        Map<Integer, ProfileStatistics> result = new HashMap<>();
        if (season == null || summonerIds == null || summonerIds.isEmpty()) return result;

        Map<String, Integer> idsByRedisKey = new LinkedHashMap<>();
        for (int summonerId : summonerIds) idsByRedisKey.put(redisKey(summonerId, season.start()), summonerId);
        for (Map.Entry<String, ProfileStatistics> entry : RedisClient.get(
            new ArrayList<>(idsByRedisKey.keySet()), ProfileStatistics.class).entrySet()) {
            result.put(idsByRedisKey.get(entry.getKey()), entry.getValue());
        }

        Map<String, Integer> idsByPuuid = new LinkedHashMap<>();
        for (int summonerId : summonerIds) {
            if (result.containsKey(summonerId)) continue;
            Summoner summoner = MongoDB.findSummonerByLegacyId(summonerId);
            if (summoner != null && summoner.puuid() != null && !summoner.puuid().isBlank()) {
                idsByPuuid.putIfAbsent(summoner.puuid(), summonerId);
            }
        }

        if (idsByPuuid.isEmpty()) return result;
        Map<String, ProfileStatistics> statisticsByPuuid = MongoDB.findProfileStatistics(
                new ArrayList<>(idsByPuuid.keySet()), season.start());
        for (Map.Entry<String, ProfileStatistics> entry : statisticsByPuuid.entrySet()) {
            ProfileStatistics statistics = entry.getValue();
            if (statistics == null) continue;
            Integer summonerId = idsByPuuid.get(entry.getKey());
            if (summonerId == null) continue;
            result.put(summonerId, statistics);
            cache(summonerId, season.start(), statistics);
        }
        return result;
    }

    public boolean refresh(int summonerId, SeasonUtils.SeasonRange season, boolean rebuild) {
        if (season == null) return false;
        Summoner summoner = MongoDB.findSummonerByLegacyId(summonerId);
        if (summoner == null || summoner.puuid() == null || summoner.region() == null) return false;
        return refresh(summoner.puuid(), LeagueShard.valueOf(summoner.region()), season, rebuild);
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

    private ProfileStatistics loadRedis(int summonerId, long timeStart) {
        return RedisClient.get(redisKey(summonerId, timeStart), ProfileStatistics.class);
    }

    private ProfileStatistics loadDatabase(int summonerId, long timeStart) {
        Summoner summoner = MongoDB.findSummonerByLegacyId(summonerId);
        return summoner == null ? null : MongoDB.findProfileStatistics(summoner.puuid(), timeStart);
    }

    private ProfileStatistics loadRedis(String puuid, long timeStart) {
        return RedisClient.get(redisKey(puuid, timeStart), ProfileStatistics.class);
    }

    private ProfileStatistics load(int summonerId, SeasonUtils.SeasonRange season) {
        ProfileStatistics statistics = loadRedis(summonerId, season.start());
        if (statistics != null) return statistics;

        statistics = loadDatabase(summonerId, season.start());
        if (statistics != null) cache(summonerId, season.start(), statistics);
        return statistics;
    }

    private void merge(ProfileStatistics statistics, List<MatchResult> matches) {
        for (MatchResult match : matches) {
            GameQueueType queue = match.queue();
            if (queue != null) statistics.add(match, queue, match.lane());
        }
    }

    private void cache(int summonerId, long timeStart, ProfileStatistics statistics) {
        RedisClient.set(redisKey(summonerId, timeStart), statistics, TTL_PROFILE_STATISTICS);
    }

    private void cache(String puuid, long timeStart, ProfileStatistics statistics) {
        RedisClient.set(redisKey(puuid, timeStart), statistics, TTL_PROFILE_STATISTICS);
    }

    private static long currentEnd(SeasonUtils.SeasonRange season) {
        return Math.min(System.currentTimeMillis(), season.end());
    }

    private static String redisKey(int summonerId, long timeStart) {
        return RedisKey.PROFILE_STATISTICS.of(summonerId, timeStart);
    }

    private static String redisKey(String puuid, long timeStart) {
        return RedisKey.PROFILE_STATISTICS.of(puuid, timeStart);
    }

}
