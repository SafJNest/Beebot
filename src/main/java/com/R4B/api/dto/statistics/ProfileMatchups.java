package com.R4B.api.dto.statistics;

import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.lol.CanonicalQueue;

public record ProfileMatchups(
    Filter filter,
    long timeStart,
    long timeEnd,
    long lastUpdate,
    Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions,
    ResponseMetadata metadata
) {

    public ProfileMatchups(
        Filter filter,
        long timeStart,
        long timeEnd,
        long lastUpdate,
        Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions
    ) {
        this(filter, timeStart, timeEnd, lastUpdate, champions, null);
    }

    public boolean hasLeafMatchups() {
        return champions != null;
    }

    public ProfileMatchups withLastUpdate(long value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, value, champions, metadata);
    }

    public ProfileMatchups withMetadata(ResponseMetadata value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, lastUpdate, champions, value);
    }
}
