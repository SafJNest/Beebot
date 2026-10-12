package com.R4B.api.dto.match;

import com.R4B.api.dto.*;

public record RankHistoryMetadata(
    String view,
    Integer season,
    String patch,
    Long requestedTimeStart,
    Long requestedTimeEnd,
    Filter filter
) {}
