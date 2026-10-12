package com.R4B.api.dto.status;

import java.util.Map;

import com.R4B.api.dto.*;

public record MongoSlowOperation(
    String command,
    String collection,
    long durationMs,
    long at,
    Map<String, Object> query
) {
}
