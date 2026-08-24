package com.safjnest.lol.model.status;

public record BotStatus(
    String status,
    LeagueMetrics league,
    java.util.List<SchedulerStatus> dispatchers,
    java.util.List<JobStatus> jobs,
    JvmMetrics process,
    SystemMetrics system,
    RedisMetrics redis,
    MongoMetrics mongo
) {

    public static final String ONLINE = "online";

    public static BotStatus online(
        LeagueMetrics league,
        java.util.List<SchedulerStatus> dispatchers,
        JvmMetrics process,
        SystemMetrics system,
        RedisMetrics redis,
        MongoMetrics mongo
    ) {
        return online(league, dispatchers, java.util.List.of(), process, system, redis, mongo);
    }

    public static BotStatus online(
        LeagueMetrics league,
        java.util.List<SchedulerStatus> dispatchers,
        java.util.List<JobStatus> jobs,
        JvmMetrics process,
        SystemMetrics system,
        RedisMetrics redis,
        MongoMetrics mongo
    ) {
        return new BotStatus(ONLINE, league, dispatchers, jobs, process, system, redis, mongo);
    }
}
