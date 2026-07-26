package com.safjnest.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedisKeyTest {

    @Test
    public void centralizesTheBalancedCachePolicy() {
        assertEquals(0, RedisKey.SUMMONER.ttlSeconds());
        assertEquals(0, RedisKey.SUMMONER_ID.ttlSeconds());
        assertEquals(0, RedisKey.ACCOUNT.ttlSeconds());
        assertEquals(0, RedisKey.ACCOUNT_BY_NAME.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.USER_ID_BY_PUUID.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.LEAGUE_ENTRIES.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.CHAMPION_MASTERIES.ttlSeconds());
        assertEquals(10 * 60, RedisKey.SPECTATOR_CURRENT.ttlSeconds());
        assertEquals(60 * 60, RedisKey.MATCH_LIST.ttlSeconds());
        assertEquals(0, RedisKey.MATCH.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.MATCH_DETAIL.ttlSeconds());
        assertEquals(5 * 60, RedisKey.MATCH_NOT_FOUND.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_DATA.ttlSeconds());
        assertEquals(0, RedisKey.TRACKER_PENDING_MATCH_LIST.ttlSeconds());
        assertEquals(12 * 60 * 60, RedisKey.CHAMPION_STATS.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_AUTOCOMPLETE.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_SEARCH.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.PROFILE_BASE.ttlSeconds());
        assertEquals(60 * 60, RedisKey.PROFILE_PAGE.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.PROFILE_RANK.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.PROFILE_RANKS.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.PROFILE_MASTERIES.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.PROFILE_STATISTICS.ttlSeconds());
        assertEquals(60 * 60, RedisKey.PROFILE_RECENT_MATCHES.ttlSeconds());
        assertEquals(0, RedisKey.LEADERBOARD_VERSION.ttlSeconds());
        assertEquals(60 * 60, RedisKey.LEADERBOARD_PAGE.ttlSeconds());
        assertEquals(12 * 60 * 60, RedisKey.LEADERBOARD_RANK_DISTRIBUTION.ttlSeconds());
        assertEquals(12 * 60 * 60, RedisKey.LEADERBOARD_TOP_REGIONS.ttlSeconds());
        assertEquals(60 * 60, RedisKey.CHAMPION_PAGE.ttlSeconds());
    }

    @Test
    public void everyKeyHasAnExplicitTtlPolicy() {
        for (RedisKey key : RedisKey.values()) {
            assertTrue(key.ttlSeconds() >= 0);
        }
    }

    @Test
    public void profileComponentKeysAreScopedByPuuid() {
        String firstRanks = RedisKey.PROFILE_RANKS.of("EUW1", "puuid-1");
        String secondRanks = RedisKey.PROFILE_RANKS.of("EUW1", "puuid-2");
        String firstMasteries = RedisKey.PROFILE_MASTERIES.of("EUW1", "puuid-1");
        String secondMasteries = RedisKey.PROFILE_MASTERIES.of("EUW1", "puuid-2");

        assertTrue(!firstRanks.equals(secondRanks));
        assertTrue(!firstMasteries.equals(secondMasteries));
    }
}
