package com.R4B.api.dto.status;

import java.util.List;

import com.R4B.api.dto.*;

public record MongoOperationsMetrics(
    int intervalSeconds,
    MongoOperationRates current,
    List<MongoOperationSample> series
) {
}
