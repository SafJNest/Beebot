package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record RedisMetrics(
    Long keys,
    Long memoryUsed
) {
}
