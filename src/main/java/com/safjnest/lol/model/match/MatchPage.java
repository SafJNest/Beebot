package com.safjnest.lol.model.match;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;

public record MatchPage(List<Match> items, int limit, int offset, long total, boolean hasMore,
                        MatchFacets.Facets facets, ResponseMetadata metadata) {

    public MatchPage(List<Match> items, int limit, int offset, long total, boolean hasMore) {
        this(items, limit, offset, total, hasMore, null, null);
    }

    public MatchPage(List<Match> items, int limit, int offset, long total, boolean hasMore,
                     ResponseMetadata metadata) {
        this(items, limit, offset, total, hasMore, null, metadata);
    }

    public MatchPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public MatchPage withMetadata(ResponseMetadata value) {
        return new MatchPage(items, limit, offset, total, hasMore, facets, value);
    }

    public MatchPage withFacets(MatchFacets.Facets value) {
        return new MatchPage(items, limit, offset, total, hasMore, value, metadata);
    }
}
