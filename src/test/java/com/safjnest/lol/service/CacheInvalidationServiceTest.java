package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.safjnest.redis.RedisKey;

public class CacheInvalidationServiceTest {

    @Test
    public void matchListRedisKeyIncludesTheExactRequestAndBatch() {
        String requestKey = "queue=TEAM_BUILDER_RANKED_SOLO:count=2:startTime=0:type=null";

        assertTrue(RedisKey.R4J_MATCH_LIST.of("EUW1", "puuid", requestKey, 0).endsWith(
            ":r4j:match:list:EUW1:puuid:" + requestKey + ":0"));
    }

    @Test
    public void staticDataKeysHaveOneDayTtl() {
        assertEquals(86_400, RedisKey.DDRAGON_ITEMS.ttlSeconds());
        assertEquals(86_400, RedisKey.DDRAGON_CHAMPIONS.ttlSeconds());
        assertEquals(86_400, RedisKey.DDRAGON_SUMMONER_SPELLS.ttlSeconds());
        assertEquals(86_400, RedisKey.DDRAGON_RUNES.ttlSeconds());
        assertEquals(86_400, RedisKey.DDRAGON_VERSIONS.ttlSeconds());
    }
}
