package com.safjnest.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedisKeyTest {

    private static final int SECONDS_PER_MINUTE = 60;
    @Test
    public void temporarilyExpiresDataCachesAfterOneMinute() {
        for (RedisKey key : RedisKey.values()) {
            switch (key) {
                case R4J_SUMMONER_REFRESH_COOLDOWN, LEADERBOARD_COUNT_LOCK, CONTEXTUAL_LEADERBOARD_BUILD,
                     CONTEXTUAL_LEADERBOARD_COUNTS, CONTEXTUAL_LEADERBOARD_SEGMENTS, CONTEXTUAL_LEADERBOARD_ACCESS -> { }
                default -> assertEquals(key.name(), SECONDS_PER_MINUTE, key.ttlSeconds());
            }
        }
        assertEquals(2 * SECONDS_PER_MINUTE, RedisKey.R4J_SUMMONER_REFRESH_COOLDOWN.ttlSeconds());
        assertEquals(SECONDS_PER_MINUTE, RedisKey.LEADERBOARD_COUNT_LOCK.ttlSeconds());
        assertEquals(0, RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.ttlSeconds());
        assertEquals(0, RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENTS.ttlSeconds());
        assertEquals(0, RedisKey.CONTEXTUAL_LEADERBOARD_ACCESS.ttlSeconds());
        assertEquals(10 * SECONDS_PER_MINUTE, RedisKey.CONTEXTUAL_LEADERBOARD_BUILD.ttlSeconds());
    }

    @Test
    public void everyKeyHasAnExplicitTtlPolicy() {
        for (RedisKey key : RedisKey.values()) {
            assertTrue(key.ttlSeconds() >= 0);
        }
    }

    @Test
    public void summonerComponentKeysAreScopedByRegionShardAndPuuid() {
        String firstRanks = RedisKey.SUMMONER_RANKS.of("EUROPE", "EUW1", "puuid-1");
        String secondRanks = RedisKey.SUMMONER_RANKS.of("EUROPE", "EUW1", "puuid-2");
        String firstMasteries = RedisKey.SUMMONER_MASTERIES.of("EUROPE", "EUW1", "puuid-1");
        String secondMasteries = RedisKey.SUMMONER_MASTERIES.of("EUROPE", "EUW1", "puuid-2");
        String summonerOverview = RedisKey.SUMMONER_OVERVIEW.of("EUROPE", "EUW1", "puuid-1");
        String summonerStatistics = RedisKey.SUMMONER_STATISTICS.of("EUROPE", "EUW1", "puuid-1", "filter");
        String summonerActivity = RedisKey.SUMMONER_ACTIVITY.of("EUROPE", "EUW1", "puuid-1", "filter");
        String summonerMatchups = RedisKey.SUMMONER_MATCHUPS.of("EUROPE", "EUW1", "puuid-1", "filter");
        String summonerFacets = RedisKey.SUMMONER_MATCH_FACETS.of("EUROPE", "EUW1", "puuid-1", "filter");
        String rankHistory = RedisKey.SUMMONER_RANK_HISTORY.of("EUROPE", "EUW1", "puuid-1", 16);

        assertTrue(!firstRanks.equals(secondRanks));
        assertTrue(!firstMasteries.equals(secondMasteries));
        assertTrue(summonerOverview.endsWith("los:EUROPE:EUW1:summoner:puuid-1:overview"));
        assertTrue(summonerStatistics.endsWith("los:EUROPE:EUW1:summoner:puuid-1:statistics:filter"));
        assertTrue(summonerActivity.endsWith("los:EUROPE:EUW1:summoner:puuid-1:activity:filter"));
        assertTrue(summonerMatchups.endsWith("los:EUROPE:EUW1:summoner:puuid-1:matchups:filter"));
        assertTrue(summonerFacets.endsWith("los:EUROPE:EUW1:summoner:puuid-1:facets:filter"));
        assertTrue(rankHistory.endsWith("los:EUROPE:EUW1:summoner:puuid-1:rank-history:v2:16"));
        assertTrue(RedisKey.SUMMONER_SEARCH.of("EUROPE", "EUW1", "query").endsWith("los:EUROPE:EUW1:summoner:search:query"));
        assertTrue(RedisKey.SUMMONER_AUTOCOMPLETE.of("EUROPE", "EUW1", "query").endsWith("los:EUROPE:EUW1:summoner:autocomplete:query"));
        assertTrue(RedisKey.CHAMPION_PAGE.of(157, "page-key").endsWith("los:champion:157:page:page-key"));
        assertTrue(RedisKey.CHAMPION_STATS.of(157, "stats-key").endsWith("los:champion:157:stats:stats-key"));
        assertTrue(RedisKey.CHAMPION_TIER_LIST.of("tier-key").endsWith("los:champion:tier-list:tier-key"));
        assertTrue(RedisKey.LEADERBOARD_COUNT.of("RANKED_SOLO_5X5", "EUW1", "ALL", "UTILITY", "40")
                .endsWith("los:leaderboard:count:RANKED_SOLO_5X5:EUW1:ALL:UTILITY:40"));
    }

    @Test
    public void separatesR4jAndLeagueOsNamespaces() {
        assertTrue(RedisKey.R4J_SUMMONER.of("EUW1", "id").contains(":r4j:"));
        assertTrue(RedisKey.R4J_TIMELINE.of("EUROPE", "EUW1_123")
                .endsWith("r4j:match:timeline:EUROPE:EUW1_123"));
        assertTrue(RedisKey.MATCH_DETAIL.of("EUROPE", "EUW1", "EUW1_123").endsWith("los:EUROPE:EUW1:match:EUW1_123:detail"));
    }
}
