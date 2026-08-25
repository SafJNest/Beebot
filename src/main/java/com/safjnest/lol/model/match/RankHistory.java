package com.safjnest.lol.model.match;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;

public record RankHistory(List<RankHistoryMatch> items, long total, ResponseMetadata metadata) {

    public RankHistory {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public RankHistory withMetadata(ResponseMetadata value) {
        return new RankHistory(items, total, value);
    }
}
