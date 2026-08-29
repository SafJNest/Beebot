package com.safjnest.redis;

import com.safjnest.App;

import java.time.Duration;

public enum RedisKey {

    R4J_SUMMONER("r4j:summoner:by-id:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    R4J_SUMMONER_ID("r4j:summoner:id-by-puuid:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    R4J_ACCOUNT("r4j:account:by-puuid:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    R4J_ACCOUNT_BY_NAME("r4j:account:by-name:%s:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    R4J_USER_ID_BY_PUUID("r4j:account:user-id-by-puuid:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    R4J_LEAGUE_ENTRIES("r4j:league:entries:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    R4J_CHAMPION_MASTERIES("r4j:league:champion-masteries:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    R4J_SPECTATOR_CURRENT("r4j:match:current:%s:%s", Duration.ofSeconds(60)), // Original: 60 seconds.
    R4J_MATCH_LIST("r4j:match:list:%s:%s:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    R4J_MATCH("r4j:match:by-id:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    R4J_SUMMONER_REFRESH_COOLDOWN("r4j:summoner:refresh:cooldown:%s:%s", Duration.ofSeconds(60)), // Original: 2 minutes.

    MATCH_DETAIL("los:%s:%s:match:%s:detail", Duration.ofSeconds(60)), // Original: 6 hours.
    SUMMONER_DATA("los:%s:%s:summoner:%s:data", Duration.ofSeconds(60)), // Original: 1 hour.
    SUMMONER_AUTOCOMPLETE("los:%s:%s:summoner:autocomplete:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    SUMMONER_SEARCH("los:%s:%s:summoner:search:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    SUMMONER("los:%s:%s:summoner:%s", Duration.ofSeconds(60)), // Original: 4 hours.
    SUMMONER_OVERVIEW("los:%s:%s:summoner:%s:overview", Duration.ofSeconds(60)), // Original: 4 hours.
    SUMMONER_RANK("los:%s:%s:summoner:%s:rank", Duration.ofSeconds(60)), // Original: 4 hours.
    SUMMONER_RANKS("los:%s:%s:summoner:%s:ranks", Duration.ofSeconds(60)), // Original: 4 hours.
    SUMMONER_MASTERIES("los:%s:%s:summoner:%s:masteries", Duration.ofSeconds(60)), // Original: 12 hours.
    SUMMONER_STATISTICS("los:%s:%s:summoner:%s:statistics:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    SUMMONER_ACTIVITY("los:%s:%s:summoner:%s:activity:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    SUMMONER_MATCHUPS("los:%s:%s:summoner:%s:matchups:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    SUMMONER_RECENT_MATCHES("los:%s:%s:summoner:%s:recent-matches:%s", Duration.ofSeconds(60)), // Original: 30 minutes.
    SUMMONER_RANK_HISTORY("los:%s:%s:summoner:%s:rank-history:%s", Duration.ofDays(1)),
    LEADERBOARD_VERSION("los:leaderboard:version", Duration.ofSeconds(60)), // Original: persistent.
    LEADERBOARD_PAGE("los:leaderboard:page:%s:%s:%s:%s:%s:%s", Duration.ofSeconds(60)), // Original: 1 day.
    LEADERBOARD_COUNT("los:leaderboard:count:%s:%s:%s:%s", Duration.ofHours(12)),
    LEADERBOARD_COUNT_LOCK("los:leaderboard:count-lock:%s:%s:%s:%s", Duration.ofMinutes(1)),
    LEADERBOARD_RANK_DISTRIBUTION("los:leaderboard:rank-distribution:%s:%s:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    LEADERBOARD_TOP_REGIONS("los:leaderboard:top-regions:%s:%s:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    CHAMPION_STATS("los:champion:%s:stats:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    CHAMPION_PAGE("los:champion:%s:page:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    CHAMPION_TIER_LIST("los:champion:tier-list:%s", Duration.ofSeconds(60)), // Original: 1 day.

    STATUS_GAME_QUEUE("status:game-queue",Duration.ofDays(10)),
    STATUS_GAMES_ANALYZED("status:games-analyzed", Duration.ofDays(10)),
    STATUS_TOTAL_SUMMONERS("status:total-summoners", Duration.ofDays(10)),
    STATUS_TOTAL_MASTERIES("status:total-masteries", Duration.ofDays(10)),
    STATUS_RANKS_BY_QUEUE("status:ranks-by-queue", Duration.ofDays(10)),
    STATUS_TRACKED_SUMMONERS("status:tracked-summoners", Duration.ofDays(10));

    private final String pattern;
    private final Duration ttl;
    private final String prefix;

    RedisKey(String pattern, Duration ttl) {
        this.pattern = pattern;
        this.ttl = ttl;
        this.prefix = App.isTesting() ? "beebot_test:lol" : "beebot:lol";
    }

    public String of(Object... args) {
        return String.format(prefix + ":" + pattern, args);
    }

    public int ttlSeconds() {
        return Math.toIntExact(ttl.toSeconds());
    }
}
