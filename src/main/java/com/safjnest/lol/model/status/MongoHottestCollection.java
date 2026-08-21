package com.safjnest.lol.model.status;

public record MongoHottestCollection(
    String name,
    double opsPerSecond,
    long ops
) {
}
