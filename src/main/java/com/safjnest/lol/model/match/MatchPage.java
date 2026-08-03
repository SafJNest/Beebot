package com.safjnest.lol.model.match;

import java.util.List;

public record MatchPage(List<MatchResult> items, int limit, int offset, long total, boolean hasMore) {

    public MatchPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
