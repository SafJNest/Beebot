package com.safjnest.lol.model.status;

import java.util.List;
import java.util.Map;

public record MongoPerformanceMetrics(
    MongoHottestCollection hottestNow,
    MongoHottestCollection hottestRecent,
    int recentWindowSeconds,
    int slowWindowSeconds,
    Map<String, Double> avgMsByCommand,
    List<MongoCollectionPerformance> collections,
    List<MongoSlowOperation> slowest
) {
}
