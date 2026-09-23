package com.safjnest.redis;

import com.safjnest.App;

import java.time.Duration;

public enum RedisKey {

    R4J_SUMMONER("r4j:summoner:by-id:%s:%s", Duration.ofHours(1)),
    R4J_SUMMONER_ID("r4j:summoner:id-by-puuid:%s:%s", Duration.ofHours(1)),
    R4J_ACCOUNT("r4j:account:by-puuid:%s:%s", Duration.ofHours(1)),
    R4J_ACCOUNT_BY_NAME("r4j:account:by-name:%s:%s:%s", Duration.ofHours(1)),
    R4J_USER_ID_BY_PUUID("r4j:account:user-id-by-puuid:%s:%s", Duration.ofHours(6)),
    R4J_LEAGUE_ENTRIES("r4j:league:entries:%s:%s", Duration.ofHours(1)),
    R4J_CHAMPION_MASTERIES("r4j:league:champion-masteries:%s:%s", Duration.ofHours(1)),
    R4J_SPECTATOR_CURRENT("r4j:match:current:%s:%s", Duration.ofSeconds(60)),
    R4J_MATCH_LIST("r4j:match:list:%s:%s:%s:%s", Duration.ofHours(1)),
    R4J_MATCH("r4j:match:by-id:%s:%s", Duration.ofHours(1)),
    R4J_TIMELINE("r4j:match:timeline:%s:%s", Duration.ofMinutes(2)),
    R4J_SUMMONER_REFRESH_COOLDOWN("r4j:summoner:refresh:cooldown:%s:%s", Duration.ofMinutes(2)),

    DDRAGON_ITEMS("r4j:static:items:%s", Duration.ofDays(1)),
    DDRAGON_CHAMPIONS("r4j:static:champions:%s", Duration.ofDays(1)),
    DDRAGON_SUMMONER_SPELLS("r4j:static:summoner-spells:%s", Duration.ofDays(1)),
    DDRAGON_RUNES("r4j:static:runes:%s", Duration.ofDays(1)),
    DDRAGON_VERSIONS("r4j:static:versions:%s", Duration.ofDays(1)),

    MATCH_DETAIL("los:%s:%s:match:%s:detail", Duration.ofHours(6)),
    SUMMONER_DATA("los:%s:%s:summoner:%s:data", Duration.ofHours(1)),
    SUMMONER_AUTOCOMPLETE("los:%s:%s:summoner:autocomplete:%s", Duration.ofHours(6)),
    SUMMONER_SEARCH("los:%s:%s:summoner:search:%s", Duration.ofHours(1)),
    SUMMONER("los:%s:%s:summoner:%s", Duration.ofHours(4)),
    SUMMONER_OVERVIEW("los:%s:%s:summoner:%s:overview", Duration.ofHours(4)),
    SUMMONER_RANK("los:%s:%s:summoner:%s:rank", Duration.ofHours(4)),
    SUMMONER_RANKS("los:%s:%s:summoner:%s:ranks", Duration.ofHours(4)),
    SUMMONER_MASTERIES("los:%s:%s:summoner:%s:masteries", Duration.ofHours(12)),
    SUMMONER_STATISTICS("los:%s:%s:summoner:%s:statistics:%s", Duration.ofHours(12)),
    SUMMONER_ACTIVITY("los:%s:%s:summoner:%s:activity:%s", Duration.ofHours(12)),
    SUMMONER_MATCHUPS("los:%s:%s:summoner:%s:matchups:%s", Duration.ofHours(12)),
    SUMMONER_RANK_HISTORY("los:%s:%s:summoner:%s:rank-history:%s", Duration.ofDays(1)),
    LEADERBOARD_PAGE("los:leaderboard:page:%s:%s:%s:%s:%s:%s:%s", Duration.ofDays(1)),
    LEADERBOARD_COUNT("los:leaderboard:count:%s:%s:%s:%s:%s", Duration.ofHours(12)),
    LEADERBOARD_COUNT_LOCK("los:leaderboard:count-lock:%s:%s:%s:%s:%s", Duration.ofMinutes(1)),
    LEADERBOARD_RANK_DISTRIBUTION("los:leaderboard:rank-distribution:%s:%s", Duration.ofHours(12)),
    LEADERBOARD_TOP_REGIONS("los:leaderboard:top-regions:%s:%s", Duration.ofHours(12)),
    CONTEXTUAL_LEADERBOARD_SEGMENT("los:leaderboard:%s:%s:%s", Duration.ofHours(6)),
    CONTEXTUAL_LEADERBOARD_COUNTS("los:leaderboard:counts:%s:%s", Duration.ZERO),
    CONTEXTUAL_LEADERBOARD_SEGMENTS("los:leaderboard:segments", Duration.ZERO),
    CONTEXTUAL_LEADERBOARD_ACCESS("los:leaderboard:access", Duration.ZERO),
    CONTEXTUAL_RECORD_SEGMENT("los:ranking:%s:%s:%s:%s", Duration.ofHours(6)),
    CONTEXTUAL_RECORD_SEGMENTS("los:ranking:segments", Duration.ZERO),
    CONTEXTUAL_RECORD_ACCESS("los:ranking:access", Duration.ZERO),
    CONTEXTUAL_LEADERBOARD_BUILD("los:leaderboard:building:%s", Duration.ofMinutes(10)),
    CONTEXTUAL_RANKING_BUILD("los:ranking:building:%s", Duration.ofMinutes(10)),
    CHAMPION_STATS("los:champion:%s:stats:%s", Duration.ofHours(12)),
    CHAMPION_PAGE("los:champion:%s:page:%s", Duration.ofHours(1)),
    CHAMPION_TIER_LIST("los:champion:tier-list:%s", Duration.ofDays(1)),

    STATUS_GAME_QUEUE("status:game-queue", Duration.ofDays(10)),
    STATUS_GAMES_ANALYZED("status:games-analyzed", Duration.ofDays(10)),
    STATUS_TOTAL_SUMMONERS("status:total-summoners", Duration.ofDays(10)),
    STATUS_TOTAL_MASTERIES("status:total-masteries", Duration.ofDays(10)),
    STATUS_RANKS_BY_QUEUE("status:ranks-by-queue", Duration.ofDays(10)),
    STATUS_TRACKED_SUMMONERS("status:tracked-summoners", Duration.ofDays(10));

    private final String pattern;
    private final Duration ttl;

    RedisKey(String pattern, Duration ttl) {
        this.pattern = pattern;
        this.ttl = ttl;
    }

    private String prefix() {
        return App.isTesting() ? "beebot_test:lol" : "beebot:lol";
    }

    public String of(Object... args) {
        return String.format(prefix() + ":" + pattern, args);
    }

    public int ttlSeconds() {
        return Math.toIntExact(ttl.toSeconds());
    }
}
