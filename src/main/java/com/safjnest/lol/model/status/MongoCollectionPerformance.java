package com.safjnest.lol.model.status;

public record MongoCollectionPerformance(
    String name,
    long count,
    double avgMs,
    long maxMs
) {
}
