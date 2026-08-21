package com.safjnest.lol.model.status;

public record BotStatus(
    String status,
    LeagueMetrics league,
    java.util.List<RequestDispatcherStatus> dispatchers,
    JvmMetrics process,
    SystemMetrics system,
    RedisMetrics redis,
    MongoMetrics mongo
) {

    public static final String ONLINE = "online";

    public static BotStatus online(
        LeagueMetrics league,
        java.util.List<RequestDispatcherStatus> dispatchers,
        JvmMetrics process,
        SystemMetrics system,
        RedisMetrics redis,
        MongoMetrics mongo
    ) {
        return new BotStatus(ONLINE, league, dispatchers, process, system, redis, mongo);
    }
}
