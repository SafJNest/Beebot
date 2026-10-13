package com.safjnest.spring.nosql;

public record MongoServerStatusSnapshot(
    MongoCommandMonitor.ClientOpcounters opcounters,
    Long connections,
    Long residentMb,
    Long virtualMb
) {
}
