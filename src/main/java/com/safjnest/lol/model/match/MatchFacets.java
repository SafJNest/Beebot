package com.safjnest.lol.model.match;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;

public record MatchFacets(long total, Facets facets, ResponseMetadata metadata) {

    public MatchFacets(long total, Facets facets) {
        this(total, facets, null);
    }

    public record Facets(
        List<Option<String>> queue,
        List<Option<String>> lane,
        List<Option<Integer>> champion,
        List<Option<String>> patch,
        List<SeasonOption> season,
        Range range
    ) {
        public Facets {
            queue = queue == null ? List.of() : List.copyOf(queue);
            lane = lane == null ? List.of() : List.copyOf(lane);
            champion = champion == null ? List.of() : List.copyOf(champion);
            patch = patch == null ? List.of() : List.copyOf(patch);
            season = season == null ? List.of() : List.copyOf(season);
        }
    }

    public record Option<T>(T value, long total, boolean selected) {}

    public record SeasonOption(String value, Integer year, long total, boolean selected) {}

    public record Range(Long min, Long max) {}

    public MatchFacets withMetadata(ResponseMetadata value) {
        return new MatchFacets(total, facets, value);
    }
}
