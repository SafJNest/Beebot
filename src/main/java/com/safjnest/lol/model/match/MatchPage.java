package com.safjnest.lol.model.match;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;

public record MatchPage(List<MatchResult> items, int limit, int offset, long total, boolean hasMore,
                        ResponseMetadata metadata) {

    public MatchPage(List<MatchResult> items, int limit, int offset, long total, boolean hasMore) {
        this(items, limit, offset, total, hasMore, null);
    }

    public MatchPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public MatchPage withMetadata(ResponseMetadata value) {
        return new MatchPage(items, limit, offset, total, hasMore, value);
    }
}
