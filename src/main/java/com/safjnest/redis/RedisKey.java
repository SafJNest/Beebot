package com.safjnest.redis;

import com.safjnest.App;

public enum RedisKey {

    SUMMONER("summoner:by-id:%s:%s"),
    SUMMONER_ID("summoner:id-by-puuid:%s:%s"),
    ACCOUNT("account:by-puuid:%s:%s"),
    ACCOUNT_BY_NAME("account:by-name:%s:%s:%s"),
    USER_ID_BY_PUUID("account:user-id-by-puuid:%s:%s"),
    LEAGUE_ENTRIES("league:entries:%s:%s"),
    CHAMPION_MASTERIES("league:champion-masteries:%s:%s"),
    SPECTATOR_CURRENT("match:current:%s:%s"),
    ADVANCED_LOL_DATA("user:advanced-lol-data:%s:%s:%s:%s"),
    MATCH_LIST("match:list:%s:%s:%s:%s"),
    MATCH("match:by-id:%s:%s"),
    MATCH_DETAIL("match:detail:%s:%s"),
    MATCH_NOT_FOUND("match:not-found:%s:%s"),
    SUMMONER_DATA("user:summoner-data:%s:%s"),
    TRACKER_PENDING_MATCH_LIST("queue:tracker:pending-matches"),
    MOST_USED_BUILD("stats:build:most-used:%s"),
    HIGH_WINRATE_BUILD("stats:build:high-winrate:%s"),
    CHAMPION_STATS("stats:champion:%s:%s"),
    SUMMONER_AUTOCOMPLETE("summoner:autocomplete:%s:%s"),
    SUMMONER_SEARCH("summoner:search:%s:%s"),
    PROFILE_BASE("profile:base:%s:%s"),
    PROFILE_PAGE("profile:page:%s:%s"),
    PROFILE_RANK("profile:rank:%s"),
    PROFILE_RANKS("profile:ranks:%s"),
    PROFILE_MASTERIES("profile:masteries:%s"),
    PROFILE_STATISTICS("profile:statistics:%s:%s"),
    LEADERBOARD_PAGE("leaderboard:page:%s:%s:%s:%s"),
    LEADERBOARD_TOTAL("leaderboard:total:%s:%s:%s"),
    LEADERBOARD_ROWS("leaderboard:rows:%s:%s:%s:%s:%s"),
    LEADERBOARD_RANK_DISTRIBUTION("leaderboard:rank-distribution:%s:%s"),
    LEADERBOARD_TOP_REGIONS("leaderboard:top-regions:%s:%s"),
    CHAMPION_PAGE("champion:page:%s");

    private final String pattern;
    private final String database;

    RedisKey(String pattern) {
        this.pattern = pattern;
        this.database = App.isTesting() ? "beebot_test:lol" : "beebot:lol";
    }

    public String of(Object... args) {
        return String.format(database + ":" + pattern, args);
    }
}
