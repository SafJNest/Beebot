package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record MongoHottestCollection(
    String name,
    double opsPerSecond,
    long ops
) {
}
