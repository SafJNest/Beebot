package com.safjnest.lol.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ProfileMatch;
import com.safjnest.lol.model.ProfileStatistics;
import com.safjnest.lol.model.ProfileStatisticsRow;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.KryoUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

/** Read-through and write-through owner of the single seasonal profile aggregate. */
public class ProfileStatisticsService {

    private static final int TTL_PROFILE_STATISTICS = 60 * 60;

    /** Redis -> database -> complete seasonal build. A fallback build is persisted in both stores. */
    public ProfileStatistics get(int summonerId, LeagueHandler.SeasonRange season) {
        if (season == null) return new ProfileStatistics(0);

        ProfileStatistics statistics = loadRedis(summonerId, season.start());
        if (statistics != null) return statistics;

        statistics = loadDatabase(summonerId, season.start());
        if (statistics != null) {
            cache(summonerId, season.start(), statistics);
            return statistics;
        }

        statistics = build(summonerId, season.start(), currentEnd(season));
        persist(summonerId, season.start(), statistics);
        return statistics;
    }

    /** Refreshes from the persisted watermark; rebuild starts again at the beginning of the season. */
    public boolean refresh(int summonerId, LeagueHandler.SeasonRange season, boolean rebuild) {
        if (season == null) return false;

        ProfileStatistics statistics = rebuild ? null : loadDatabase(summonerId, season.start());
        if (statistics == null) {
            statistics = build(summonerId, season.start(), currentEnd(season));
        } else {
            merge(statistics, LeagueService.getProfileMatchesAfter(summonerId, statistics.timeEnd, currentEnd(season)));
        }
        return persist(summonerId, season.start(), statistics);
    }

    private ProfileStatistics loadRedis(int summonerId, long timeStart) {
        return RedisClient.get(redisKey(summonerId, timeStart), ProfileStatistics.class);
    }

    private ProfileStatistics loadDatabase(int summonerId, long timeStart) {
        ProfileStatisticsRow row = LeagueDB.getProfileStatistics(databaseKey(summonerId, timeStart));
        if (row == null || row.data() == null) return null;
        try {
            ProfileStatistics statistics = KryoUtils.decode(row.data(), ProfileStatistics.class);
            statistics.timeStart = row.timeStart();
            statistics.timeEnd = row.timeEnd();
            return statistics;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ProfileStatistics build(int summonerId, long timeStart, long untilTimeEnd) {
        ProfileStatistics statistics = new ProfileStatistics(timeStart);
        merge(statistics, LeagueService.getProfileMatchesAfter(summonerId, Math.max(0, timeStart - 1), untilTimeEnd));
        return statistics;
    }

    private void merge(ProfileStatistics statistics, List<ProfileMatch> matches) {
        for (ProfileMatch match : matches) {
            GameQueueType queue = match.queue();
            if (queue != null) statistics.add(match, queue, match.lane());
        }
    }

    /** DB is authoritative: cache only after the BLOB upsert succeeds. */
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

    private static long currentEnd(LeagueHandler.SeasonRange season) {
        return Math.min(System.currentTimeMillis(), season.end());
    }

    private static String databaseKey(int summonerId, long timeStart) {
        return Base64.getEncoder().encodeToString((summonerId + "|" + timeStart).getBytes(StandardCharsets.UTF_8));
    }

    private static String redisKey(int summonerId, long timeStart) {
        return RedisKey.PROFILE_STATISTICS.of(summonerId, timeStart);
    }
}
