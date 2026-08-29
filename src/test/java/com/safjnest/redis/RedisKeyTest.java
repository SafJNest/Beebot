package com.safjnest.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedisKeyTest {

    @Test
    public void centralizesTheBalancedCachePolicy() {
        assertEquals(0, RedisKey.R4J_SUMMONER.ttlSeconds());
        assertEquals(0, RedisKey.R4J_SUMMONER_ID.ttlSeconds());
        assertEquals(0, RedisKey.R4J_ACCOUNT.ttlSeconds());
        assertEquals(0, RedisKey.R4J_ACCOUNT_BY_NAME.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.R4J_USER_ID_BY_PUUID.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.R4J_LEAGUE_ENTRIES.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.R4J_CHAMPION_MASTERIES.ttlSeconds());
        assertEquals(60, RedisKey.R4J_SPECTATOR_CURRENT.ttlSeconds());
        assertEquals(60 * 60, RedisKey.R4J_MATCH_LIST.ttlSeconds());
        assertEquals(0, RedisKey.R4J_MATCH.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.MATCH_DETAIL.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_DATA.ttlSeconds());
        assertEquals(12 * 60 * 60, RedisKey.CHAMPION_STATS.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_AUTOCOMPLETE.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_SEARCH.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER.ttlSeconds());
        assertEquals(2 * 60, RedisKey.R4J_SUMMONER_REFRESH_COOLDOWN.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_OVERVIEW.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER_RANK.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER_RANKS.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER_MASTERIES.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER_STATISTICS.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER_ACTIVITY.ttlSeconds());
        assertEquals(6 * 60 * 60, RedisKey.SUMMONER_MATCHUPS.ttlSeconds());
        assertEquals(60 * 60, RedisKey.SUMMONER_RECENT_MATCHES.ttlSeconds());
        assertEquals(24 * 60 * 60, RedisKey.SUMMONER_RANK_HISTORY.ttlSeconds());
        assertEquals(0, RedisKey.LEADERBOARD_VERSION.ttlSeconds());
        assertEquals(60 * 60, RedisKey.LEADERBOARD_PAGE.ttlSeconds());
        assertEquals(12 * 60 * 60, RedisKey.LEADERBOARD_COUNT.ttlSeconds());
        assertEquals(60, RedisKey.LEADERBOARD_COUNT_LOCK.ttlSeconds());
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
    public void summonerComponentKeysAreScopedByRegionShardAndPuuid() {
        String firstRanks = RedisKey.SUMMONER_RANKS.of("EUROPE", "EUW1", "puuid-1");
        String secondRanks = RedisKey.SUMMONER_RANKS.of("EUROPE", "EUW1", "puuid-2");
        String firstMasteries = RedisKey.SUMMONER_MASTERIES.of("EUROPE", "EUW1", "puuid-1");
        String secondMasteries = RedisKey.SUMMONER_MASTERIES.of("EUROPE", "EUW1", "puuid-2");
        String summonerOverview = RedisKey.SUMMONER_OVERVIEW.of("EUROPE", "EUW1", "puuid-1");
        String summonerStatistics = RedisKey.SUMMONER_STATISTICS.of("EUROPE", "EUW1", "puuid-1", "filter");
        String summonerActivity = RedisKey.SUMMONER_ACTIVITY.of("EUROPE", "EUW1", "puuid-1", "filter");
        String summonerMatchups = RedisKey.SUMMONER_MATCHUPS.of("EUROPE", "EUW1", "puuid-1", "filter");
        String rankHistory = RedisKey.SUMMONER_RANK_HISTORY.of("EUROPE", "EUW1", "puuid-1", 16);

        assertTrue(!firstRanks.equals(secondRanks));
        assertTrue(!firstMasteries.equals(secondMasteries));
        assertTrue(summonerOverview.endsWith("ls:EUROPE:EUW1:summoner:puuid-1:overview"));
        assertTrue(summonerStatistics.endsWith("ls:EUROPE:EUW1:summoner:puuid-1:statistics:filter"));
        assertTrue(summonerActivity.endsWith("ls:EUROPE:EUW1:summoner:puuid-1:activity:filter"));
        assertTrue(summonerMatchups.endsWith("ls:EUROPE:EUW1:summoner:puuid-1:matchups:filter"));
        assertTrue(rankHistory.endsWith("ls:EUROPE:EUW1:summoner:puuid-1:rank-history:16"));
        assertTrue(RedisKey.SUMMONER_SEARCH.of("EUROPE", "EUW1", "query").endsWith("ls:EUROPE:EUW1:summoner:search:query"));
        assertTrue(RedisKey.SUMMONER_AUTOCOMPLETE.of("EUROPE", "EUW1", "query").endsWith("ls:EUROPE:EUW1:summoner:autocomplete:query"));
        assertTrue(RedisKey.CHAMPION_PAGE.of(157, "page-key").endsWith("ls:champion:157:page:page-key"));
        assertTrue(RedisKey.CHAMPION_STATS.of(157, "stats-key").endsWith("ls:champion:157:stats:stats-key"));
        assertTrue(RedisKey.CHAMPION_TIER_LIST.of("tier-key").endsWith("ls:champion:tier-list:tier-key"));
        assertTrue(RedisKey.LEADERBOARD_COUNT.of(3, "RANKED_SOLO_5X5", "EUW1", "ALL", "UTILITY", "40")
                .endsWith("ls:leaderboard:count:3:RANKED_SOLO_5X5:EUW1:ALL:UTILITY:40"));
    }

    @Test
    public void separatesR4jAndLeagueOsNamespaces() {
        assertTrue(RedisKey.R4J_SUMMONER.of("EUW1", "id").contains(":r4j:"));
        assertTrue(RedisKey.MATCH_DETAIL.of("EUROPE", "EUW1", "EUW1_123").endsWith("ls:EUROPE:EUW1:match:EUW1_123:detail"));
        assertTrue(RedisKey.SUMMONER_RECENT_MATCHES.of("EUROPE", "EUW1", "puuid-1", "filter").contains(":ls:EUROPE:EUW1:summoner:puuid-1:"));
    }
}
