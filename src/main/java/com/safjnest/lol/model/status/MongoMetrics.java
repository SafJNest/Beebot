package com.safjnest.lol.model.status;

public record MongoMetrics(
    MongoOperationsMetrics operations,
    MongoPerformanceMetrics performance,
    Long connections,
    MongoMemoryMetrics memory
) {
}
