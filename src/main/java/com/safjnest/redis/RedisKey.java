package com.safjnest.redis;

import com.safjnest.App;

import java.time.Duration;

public enum RedisKey {

    SUMMONER("r4j:summoner:by-id:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    SUMMONER_ID("r4j:summoner:id-by-puuid:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    ACCOUNT("r4j:account:by-puuid:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    ACCOUNT_BY_NAME("r4j:account:by-name:%s:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    USER_ID_BY_PUUID("r4j:account:user-id-by-puuid:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    LEAGUE_ENTRIES("r4j:league:entries:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    CHAMPION_MASTERIES("r4j:league:champion-masteries:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    SPECTATOR_CURRENT("r4j:match:current:%s:%s", Duration.ofSeconds(60)), // Original: 60 seconds.
    MATCH_LIST("r4j:match:list:%s:%s:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    MATCH("r4j:match:by-id:%s:%s", Duration.ofSeconds(60)), // Original: persistent.
    MATCH_DETAIL("match:detail:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    MATCH_NOT_FOUND("r4j:match:not-found:%s:%s", Duration.ofSeconds(60)), // Original: 5 minutes.
    SUMMONER_DATA("user:summoner-data:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    TRACKER_PENDING_MATCH_LIST("queue:tracker:pending-matches", Duration.ofSeconds(60)), // Original: persistent.
    CHAMPION_STATS("stats:champion:%s:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    SUMMONER_AUTOCOMPLETE("summoner:autocomplete:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    SUMMONER_SEARCH("summoner:search:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    PROFILE_BASE("profile:base:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    SUMMONER_REFRESH_COOLDOWN("r4j:summoner:refresh:cooldown:%s:%s", Duration.ofSeconds(60)), // Original: 2 minutes.
    PROFILE_PAGE("profile:page:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    PROFILE_RANK("profile:rank:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    PROFILE_RANKS("profile:ranks:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    PROFILE_MASTERIES("profile:masteries:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    PROFILE_STATISTICS("profile:statistics:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    PROFILE_ACTIVITY("profile:activity:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    PROFILE_MATCHUPS("profile:matchups:%s:%s", Duration.ofSeconds(60)), // Original: 6 hours.
    PROFILE_RECENT_MATCHES("profile:recent-matches:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    LEADERBOARD_VERSION("leaderboard:version", Duration.ofSeconds(60)), // Original: persistent.
    LEADERBOARD_PAGE("leaderboard:page:%s:%s:%s:%s:%s:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    LEADERBOARD_RANK_DISTRIBUTION("leaderboard:rank-distribution:%s:%s:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    LEADERBOARD_TOP_REGIONS("leaderboard:top-regions:%s:%s:%s", Duration.ofSeconds(60)), // Original: 12 hours.
    CHAMPION_PAGE("champion:page:%s", Duration.ofSeconds(60)), // Original: 1 hour.
    CHAMPION_TIER_LIST("champion:tier-list:v5:%s", Duration.ofSeconds(60)); // Original: 1 hour.

    private final String pattern;
    private final Duration ttl;
    private final String database;

    RedisKey(String pattern, Duration ttl) {
        this.pattern = pattern;
        this.ttl = ttl;
        this.database = App.isTesting() ? "beebot_test:lol" : "beebot:lol";
    }

    public String of(Object... args) {
        return String.format(database + ":" + pattern, args);
    }

    public int ttlSeconds() {
        return Math.toIntExact(ttl.toSeconds());
    }
}
