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
    MATCH_DETAIL("match:detail:v2:%s:%s"),
    SUMMONER_DATA("user:summoner-data:%s:%s"),
    TRACKER_PENDING_MATCH_LIST("queue:tracker:pending-matches"),
    MOST_USED_BUILD("stats:build:most-used:%s"),
    HIGH_WINRATE_BUILD("stats:build:high-winrate:%s"),
    CHAMPION_STATS("stats:champion:%s:%s"),
    SUMMONER_AUTOCOMPLETE("summoner:autocomplete:%s:%s"),
    SUMMONER_SEARCH("summoner:search:v2:%s:%s"),
    PROFILE_BASE("profile:base:v2:%s:%s"),
    PROFILE_RANK("profile:rank:v2:%s"),
    PROFILE_RANKS("profile:ranks:v2:%s"),
    PROFILE_MASTERIES("profile:masteries:v2:%s"),
    PROFILE_STATISTICS("profile:statistics:v3:%s:%s"),
    LEADERBOARD_PAGE("leaderboard:page:v2:%s:%s:%s:%s"),
    LEADERBOARD_RANK_DISTRIBUTION("leaderboard:rank-distribution:v2:%s:%s"),
    LEADERBOARD_TOP_REGIONS("leaderboard:top-regions:v2:%s:%s"),
    CHAMPION_PAGE("champion:page:v1:%s");

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
