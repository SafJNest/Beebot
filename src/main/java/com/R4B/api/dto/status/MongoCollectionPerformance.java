package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record MongoCollectionPerformance(
    String name,
    long count,
    double avgMs,
    long maxMs
) {
}
