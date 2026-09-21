package com.safjnest.lol.model.status;

import java.util.Map;

public record MongoSlowOperation(
    String command,
    String collection,
    long durationMs,
    long at,
    Map<String, Object> query
) {
}
