package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileActivityService {

    public ProfileActivity get(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null)
            return ProfileActivity.from(List.of(), puuid, filter);
        ProfileActivity activity = RedisClient.get(redisKey(puuid, filter), ProfileActivity.class);
        if (activity != null) return activity;

        activity = MongoDB.findProfileActivity(puuid, filter);
        if (activity != null) {
            cache(puuid, filter, activity);
            return activity;
        }

        List<Match> matches = MongoDB.findProfileStatisticsMatches(puuid, shard, filter, 0, 0);
        activity = ProfileActivity.from(matches, puuid, filter);
        if (MongoDB.upsertProfileActivity(puuid, filter, activity)) cache(puuid, filter, activity);
        return activity;
    }

    // ============================================================================

    private static void cache(String puuid, Filter filter, ProfileActivity activity) {
        RedisClient.set(RedisKey.PROFILE_ACTIVITY, activity, puuid, filter.toSummonerKey());
    }

    private static String redisKey(String puuid, Filter filter) {
        return RedisKey.PROFILE_ACTIVITY.of(puuid, filter.toSummonerKey());
    }
}
