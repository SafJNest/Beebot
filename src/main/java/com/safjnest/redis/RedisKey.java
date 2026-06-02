package com.safjnest.redis;

import com.safjnest.App;

public enum RedisKey {

    SUMMONER("summoner:%s:%s"),
    SUMMONER_ID("summoner_id:%s:%s"),
    ACCOUNT("riot_account:%s:%s"),
    ACCOUNT_BY_NAME("riot_account_name:%s:%s:%s"),
    USER_ID_BY_PUUID("user_id_by_puuid:%s:%s"),
    LEAGUE_ENTRIES("league_entries:%s:%s"),
    CHAMPION_MASTERIES("champion_masteries:%s:%s"),
    SPECTATOR_CURRENT("spectator_current:%s:%s"),
    ADVANCED_LOL_DATA("advanced_lol_data:%s:%s:%s:%s"),
    MATCH_LIST("match_list:%s:%s:%s:%s"),
    MATCH("match:%s:%s"),
    SUMMONER_DATA("summoner_data:%s:%s"),
    TRACKER_PENDING_MATCH_LIST("tracker:pending_match_queue"),
    MOST_USED_BUILD("most_used_build:%s"),
    HIGH_WINRATE_BUILD("high_winrate_build:%s"),
    CHAMPION_STATS("champion_stats:%s:%s");

    private final String pattern;
    private final String database;

    RedisKey(String pattern) {
        this.pattern = pattern;
        this.database = App.isTesting() ? "beebot_test" : "beebot";
    }

    public String of(Object... args) {
        return String.format(database + ":" + pattern, args);
    }
}