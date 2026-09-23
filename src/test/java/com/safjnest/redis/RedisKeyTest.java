package com.safjnest.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedisKeyTest {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
    private static final int SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;

    @Test
    public void centralizesTheBalancedCachePolicy() {
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_SUMMONER.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_SUMMONER_ID.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_ACCOUNT.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_ACCOUNT_BY_NAME.ttlSeconds());
        assertEquals(6 * SECONDS_PER_HOUR, RedisKey.R4J_USER_ID_BY_PUUID.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_LEAGUE_ENTRIES.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_CHAMPION_MASTERIES.ttlSeconds());
        assertEquals(SECONDS_PER_MINUTE, RedisKey.R4J_SPECTATOR_CURRENT.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_MATCH_LIST.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.R4J_MATCH.ttlSeconds());
        assertEquals(2 * SECONDS_PER_MINUTE, RedisKey.R4J_TIMELINE.ttlSeconds());
        assertEquals(6 * SECONDS_PER_HOUR, RedisKey.MATCH_DETAIL.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.SUMMONER_DATA.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.CHAMPION_STATS.ttlSeconds());
        assertEquals(6 * SECONDS_PER_HOUR, RedisKey.SUMMONER_AUTOCOMPLETE.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.SUMMONER_SEARCH.ttlSeconds());
        assertEquals(4 * SECONDS_PER_HOUR, RedisKey.SUMMONER.ttlSeconds());
        assertEquals(2 * SECONDS_PER_MINUTE, RedisKey.R4J_SUMMONER_REFRESH_COOLDOWN.ttlSeconds());
        assertEquals(4 * SECONDS_PER_HOUR, RedisKey.SUMMONER_OVERVIEW.ttlSeconds());
        assertEquals(4 * SECONDS_PER_HOUR, RedisKey.SUMMONER_RANK.ttlSeconds());
        assertEquals(4 * SECONDS_PER_HOUR, RedisKey.SUMMONER_RANKS.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.SUMMONER_MASTERIES.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.SUMMONER_STATISTICS.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.SUMMONER_ACTIVITY.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.SUMMONER_MATCHUPS.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.SUMMONER_RANK_HISTORY.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.LEADERBOARD_PAGE.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.LEADERBOARD_COUNT.ttlSeconds());
        assertEquals(SECONDS_PER_MINUTE, RedisKey.LEADERBOARD_COUNT_LOCK.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.LEADERBOARD_RANK_DISTRIBUTION.ttlSeconds());
        assertEquals(12 * SECONDS_PER_HOUR, RedisKey.LEADERBOARD_TOP_REGIONS.ttlSeconds());
        assertEquals(6 * SECONDS_PER_HOUR, RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.ttlSeconds());
        assertEquals(0, RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.ttlSeconds());
        assertEquals(10 * SECONDS_PER_MINUTE, RedisKey.CONTEXTUAL_LEADERBOARD_BUILD.ttlSeconds());
        assertEquals(SECONDS_PER_HOUR, RedisKey.CHAMPION_PAGE.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.CHAMPION_TIER_LIST.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.DDRAGON_ITEMS.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.DDRAGON_CHAMPIONS.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.DDRAGON_SUMMONER_SPELLS.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.DDRAGON_RUNES.ttlSeconds());
        assertEquals(SECONDS_PER_DAY, RedisKey.DDRAGON_VERSIONS.ttlSeconds());
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
        assertTrue(summonerOverview.endsWith("los:EUROPE:EUW1:summoner:puuid-1:overview"));
        assertTrue(summonerStatistics.endsWith("los:EUROPE:EUW1:summoner:puuid-1:statistics:filter"));
        assertTrue(summonerActivity.endsWith("los:EUROPE:EUW1:summoner:puuid-1:activity:filter"));
        assertTrue(summonerMatchups.endsWith("los:EUROPE:EUW1:summoner:puuid-1:matchups:filter"));
        assertTrue(rankHistory.endsWith("los:EUROPE:EUW1:summoner:puuid-1:rank-history:16"));
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
