package com.safjnest.redis;

public enum RedisKey {

    SUMMONER("summoner:%s:%s"),
    SUMMONER_ID("summoner_id:%s:%s"),
    ACCOUNT("riot_account:%s:%s"),
    ACCOUNT_BY_NAME("riot_account_name:%s:%s:%s");

    private final String pattern;

    RedisKey(String pattern) {
        this.pattern = pattern;
    }

    public String of(Object... args) {
        return String.format(pattern, args);
    }
}