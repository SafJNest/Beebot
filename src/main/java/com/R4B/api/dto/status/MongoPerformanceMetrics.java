package com.R4B.api.dto.status;

import java.util.List;
import java.util.Map;

import com.R4B.api.dto.*;

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
