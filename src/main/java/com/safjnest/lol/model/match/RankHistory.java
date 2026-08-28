package com.safjnest.lol.model.match;

import java.util.List;

public record RankHistory(List<RankHistoryMatch> items, long total, RankHistoryMetadata metadata) {

    public RankHistory {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public RankHistory withMetadata(RankHistoryMetadata value) {
        return new RankHistory(items, total, value);
    }
}
