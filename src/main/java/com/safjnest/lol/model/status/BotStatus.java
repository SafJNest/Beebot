package com.safjnest.lol.model.status;

public record BotStatus(
    String status,
    LeagueMetrics league,
    TrackerMetrics tracker,
    WorkerMetrics workers,
    RiotMetrics riot,
    JvmMetrics process,
    SystemMetrics system,
    RedisMetrics redis
) {

    public static final String ONLINE = "online";

    public static BotStatus online(
        LeagueMetrics league,
        TrackerMetrics tracker,
        WorkerMetrics workers,
        RiotMetrics riot,
        JvmMetrics process,
        SystemMetrics system,
        RedisMetrics redis
    ) {
        return new BotStatus(ONLINE, league, tracker, workers, riot, process, system, redis);
    }
}
