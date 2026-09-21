package com.safjnest.lol.model.match;

import com.safjnest.lol.model.Filter;

public record RankHistoryMetadata(
    String view,
    Integer season,
    String patch,
    Long requestedTimeStart,
    Long requestedTimeEnd,
    Filter filter
) {}
