package com.safjnest.database.nosql;

public record MongoServerStatusSnapshot(
    MongoCommandMonitor.ClientOpcounters opcounters,
    Long connections,
    Long residentMb,
    Long virtualMb
) {
}
