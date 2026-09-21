package com.safjnest.lol.model.status;

import java.util.List;

public record MongoOperationsMetrics(
    int intervalSeconds,
    MongoOperationRates current,
    List<MongoOperationSample> series
) {
}
