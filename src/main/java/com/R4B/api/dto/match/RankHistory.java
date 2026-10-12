package com.R4B.api.dto.match;

import java.util.List;

import com.R4B.api.dto.*;

public record RankHistory(List<RankHistoryMatch> items, long total, RankHistoryMetadata metadata) {

    public RankHistory {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public RankHistory withMetadata(RankHistoryMetadata value) {
        return new RankHistory(items, total, value);
    }
}
