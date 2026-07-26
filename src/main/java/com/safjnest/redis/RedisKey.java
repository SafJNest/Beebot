package com.safjnest.redis;

import com.safjnest.App;

import java.time.Duration;

public enum RedisKey {

    SUMMONER("summoner:by-id:%s:%s", Duration.ZERO),
    SUMMONER_ID("summoner:id-by-puuid:%s:%s", Duration.ZERO),
    ACCOUNT("account:by-puuid:%s:%s", Duration.ZERO),
    ACCOUNT_BY_NAME("account:by-name:%s:%s:%s", Duration.ZERO),
    USER_ID_BY_PUUID("account:user-id-by-puuid:%s:%s", Duration.ofHours(6)),
    LEAGUE_ENTRIES("league:entries:%s:%s", Duration.ofHours(6)),
    CHAMPION_MASTERIES("league:champion-masteries:%s:%s", Duration.ofHours(6)),
    SPECTATOR_CURRENT("match:current:%s:%s", Duration.ofMinutes(10)),
    MATCH_LIST("match:list:%s:%s:%s:%s", Duration.ofHours(1)),
    MATCH("match:by-id:%s:%s", Duration.ZERO),
    MATCH_DETAIL("match:detail:%s:%s", Duration.ofHours(6)),
    MATCH_NOT_FOUND("match:not-found:%s:%s", Duration.ofMinutes(5)),
    SUMMONER_DATA("user:summoner-data:%s:%s", Duration.ofHours(1)),
    TRACKER_PENDING_MATCH_LIST("queue:tracker:pending-matches", Duration.ZERO),
    CHAMPION_STATS("stats:champion:%s:%s", Duration.ofHours(12)),
    SUMMONER_AUTOCOMPLETE("summoner:autocomplete:%s:%s", Duration.ofHours(1)),
    SUMMONER_SEARCH("summoner:search:%s:%s", Duration.ofHours(1)),
    PROFILE_BASE("profile:base:%s:%s", Duration.ofHours(6)),
    PROFILE_PAGE("profile:page:%s:%s", Duration.ofHours(1)),
    PROFILE_RANK("profile:rank:%s", Duration.ofHours(6)),
    PROFILE_RANKS("profile:ranks:%s", Duration.ofHours(6)),
    PROFILE_MASTERIES("profile:masteries:%s", Duration.ofHours(6)),
    PROFILE_STATISTICS("profile:statistics:%s:%s", Duration.ofHours(6)),
    PROFILE_RECENT_MATCHES("profile:recent-matches:%s:%s", Duration.ofHours(1)),
    LEADERBOARD_VERSION("leaderboard:version", Duration.ZERO),
    LEADERBOARD_PAGE("leaderboard:page:%s:%s:%s:%s:%s:%s", Duration.ofHours(1)),
    LEADERBOARD_RANK_DISTRIBUTION("leaderboard:rank-distribution:%s:%s:%s", Duration.ofHours(12)),
    LEADERBOARD_TOP_REGIONS("leaderboard:top-regions:%s:%s:%s", Duration.ofHours(12)),
    CHAMPION_PAGE("champion:page:%s", Duration.ofHours(1));

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
