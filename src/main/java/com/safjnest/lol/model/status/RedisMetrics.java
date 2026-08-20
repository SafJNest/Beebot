package com.safjnest.lol.model.status;

public record RedisMetrics(
    Long keys,
    Long memoryUsed
) {
}
