package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record MongoMemoryMetrics(
    Long residentMb,
    Long virtualMb
) {
}
