package com.safjnest.lol.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.ProfileStatisticsRow;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.KryoUtils;
import com.safjnest.lol.utils.SeasonUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class ProfileStatisticsService {

    private static final int MIN_PROFILE_GAMES = 5;
    private static final int TTL_PROFILE_STATISTICS = 60 * 60;

    public ProfileStatistics get(int summonerId, SeasonUtils.SeasonRange season) {
        return season == null ? null : load(summonerId, season);
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

        Map<String, Integer> idsByKey = new LinkedHashMap<>();
        for (int summonerId : summonerIds) {
            if (result.containsKey(summonerId)) continue;
            String key = databaseKey(summonerId, season.start());
            idsByKey.put(key, summonerId);
        }

        if (idsByKey.isEmpty()) return result;
        for (Map.Entry<String, ProfileStatisticsRow> entry : LeagueDB.getProfileStatistics(new ArrayList<>(idsByKey.keySet())).entrySet()) {
            ProfileStatistics statistics = decode(entry.getValue());
            if (statistics == null) continue;
            int summonerId = idsByKey.get(entry.getKey());
            result.put(summonerId, statistics);
            cache(summonerId, season.start(), statistics);
        }
        return result;
    }

    public boolean refresh(int summonerId, SeasonUtils.SeasonRange season, boolean rebuild) {
        if (season == null) return false;

        ProfileStatistics statistics = rebuild ? null : loadDatabase(summonerId, season.start());
        if (statistics == null) {
            statistics = build(summonerId, season.start(), currentEnd(season));
        } else {
            merge(statistics, LeagueService.getProfileMatchesAfter(summonerId, statistics.timeEnd, currentEnd(season)));
        }
        return persist(summonerId, season.start(), statistics);
    }

    // ============================================================================

    private ProfileStatistics loadRedis(int summonerId, long timeStart) {
        return RedisClient.get(redisKey(summonerId, timeStart), ProfileStatistics.class);
    }

    private ProfileStatistics loadDatabase(int summonerId, long timeStart) {
        String key = databaseKey(summonerId, timeStart);
        ProfileStatisticsRow row = LeagueDB.getProfileStatistics(key);
        return decode(row);
    }

    private ProfileStatistics decode(ProfileStatisticsRow row) {
        if (row == null || row.data() == null) return null;
        try {
            ProfileStatistics statistics = KryoUtils.decode(row.data(), ProfileStatistics.class);
            if (!valid(statistics)) return null;
            statistics.timeStart = row.timeStart();
            statistics.timeEnd = row.timeEnd();
            return statistics;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private ProfileStatistics load(int summonerId, SeasonUtils.SeasonRange season) {
        ProfileStatistics statistics = loadRedis(summonerId, season.start());
        if (statistics != null) return statistics;

        statistics = loadDatabase(summonerId, season.start());
        if (statistics != null) cache(summonerId, season.start(), statistics);
        return statistics;
    }

    private ProfileStatistics build(int summonerId, long timeStart, long untilTimeEnd) {
        ProfileStatistics statistics = new ProfileStatistics(timeStart);
        merge(statistics, LeagueService.getProfileMatchesAfter(summonerId, Math.max(0, timeStart - 1), untilTimeEnd));
        return statistics;
    }

    private void merge(ProfileStatistics statistics, List<MatchResult> matches) {
        for (MatchResult match : matches) {
            GameQueueType queue = match.queue();
            if (queue != null) statistics.add(match, queue, match.lane());
        }
    }

    private boolean persist(int summonerId, long timeStart, ProfileStatistics statistics) {
        String encoded = KryoUtils.encode(statistics);
        boolean saved = LeagueDB.saveProfileStatistics(
            databaseKey(summonerId, timeStart), summonerId, statistics.timeStart, statistics.timeEnd,
            Base64.getDecoder().decode(encoded)
        );
        if (saved) cache(summonerId, timeStart, statistics);
        return saved;
    }

    private void cache(int summonerId, long timeStart, ProfileStatistics statistics) {
        RedisClient.set(redisKey(summonerId, timeStart), statistics, TTL_PROFILE_STATISTICS);
    }

    private static long currentEnd(SeasonUtils.SeasonRange season) {
        return Math.min(System.currentTimeMillis(), season.end());
    }

    private static String databaseKey(int summonerId, long timeStart) {
        return Base64.getEncoder().encodeToString((summonerId + "|" + timeStart).getBytes(StandardCharsets.UTF_8));
    }

    private static String redisKey(int summonerId, long timeStart) {
        return RedisKey.PROFILE_STATISTICS.of(summonerId, timeStart);
    }

    private static boolean valid(ProfileStatistics statistics) {
        try {
            if (statistics == null || !(statistics.total instanceof Stats<?>)) return false;
            return statistics.total.games >= MIN_PROFILE_GAMES
                && validStats(statistics.queueStats, GameQueueType.class)
                && validStats(statistics.laneStats, LaneType.class)
                && validStats(statistics.championStats, Integer.class)
                && validMatches(statistics.recentMatches);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean validStats(List<?> values, Class<?> referenceType) {
        if (values == null) return false;
        for (Object value : values) {
            if (!(value instanceof Stats<?> stats)) return false;
            if (stats.reference != null && !referenceType.isInstance(stats.reference)) return false;
        }
        return true;
    }

    private static boolean validMatches(List<?> values) {
        if (values == null) return false;
        for (Object value : values) {
            if (!(value instanceof MatchResult)) return false;
        }
        return true;
    }
}
