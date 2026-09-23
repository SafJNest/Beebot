package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public final class CacheInvalidationService {

    private CacheInvalidationService() {
    }

    public static void clearRedis(RedisKey key, Object... values) {
        if (key == null) return;
        RedisClient.delete(key.of(values));
    }

    public static void clearRedis(List<String> keys) {
        RedisClient.delete(keys);
    }

    public static void clearMatchHistory(
            Summoner summoner,
            GameQueueType queue,
            int index,
            int count,
            long startTime,
            MatchlistMatchType type) {
        if (summoner == null || summoner.getPlatform() == null || summoner.getPUUID() == null
                || index < 0 || count < 0 || startTime < 0) return;

        int requestedCount = count == 0 ? MatchService.MATCH_LIST_BATCH_SIZE : count;
        String requestKey = MatchService.matchListRequestKey(queue, requestedCount, startTime, type);
        clearRedis(RedisKey.R4J_MATCH_LIST,
            summoner.getPlatform().name(), summoner.getPUUID(), requestKey, index);
    }

    // ============================================================================

}
