package com.safjnest.lol.model.status;

public record MongoMemoryMetrics(
    Long residentMb,
    Long virtualMb
) {
}
