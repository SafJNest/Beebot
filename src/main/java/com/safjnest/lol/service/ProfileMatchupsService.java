package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileMatchupsService {

    public ProfileMatchups get(String puuid, Filter filter) {
        if (puuid == null || puuid.isBlank() || filter == null) return null;
        ProfileMatchups matchups = RedisClient.get(redisKey(puuid, filter), ProfileMatchups.class);
        if (matchups != null) return matchups;

        matchups = MongoDB.findProfileMatchups(puuid, filter);
        if (matchups != null) cache(puuid, filter, matchups);
        return matchups;
    }

    public boolean refresh(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return false;
        List<Match> matches = MongoDB.findProfileStatisticsMatches(puuid, shard, filter, 0, 0);
        ProfileMatchups matchups = ProfileMatchups.from(matches, puuid, filter)
            .withLastUpdate(System.currentTimeMillis());
        boolean saved = MongoDB.upsertProfileMatchups(puuid, filter, matchups);
        if (saved) cache(puuid, filter, matchups);
        return saved;
    }

    // ============================================================================

    private static void cache(String puuid, Filter filter, ProfileMatchups matchups) {
        RedisClient.set(RedisKey.PROFILE_MATCHUPS, matchups, puuid, filter.toSummonerKey());
    }

    private static String redisKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_MATCHUPS.of(puuid, filter.toSummonerKey());
    }
}
