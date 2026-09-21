package com.safjnest.nosql;

public record MongoServerStatusSnapshot(
    MongoCommandMonitor.ClientOpcounters opcounters,
    Long connections,
    Long residentMb,
    Long virtualMb
) {
}
