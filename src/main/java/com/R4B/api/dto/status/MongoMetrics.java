package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record MongoMetrics(
    MongoOperationsMetrics operations,
    MongoPerformanceMetrics performance,
    Long connections,
    MongoMemoryMetrics memory
) {
}
