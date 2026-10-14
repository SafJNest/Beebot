package com.safjnest.lol.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "games", "wins", "winrate", "coreBuilds", "skillOrders", "augments", "prismatics", "coverage"})
public record ArenaBuildData(long games, long wins,
        List<Core> coreBuilds, @JsonInclude(JsonInclude.Include.NON_NULL) List<Build.SkillOrderOption> skillOrders,
        List<List<Option>> augments, List<Option> prismatics, Coverage coverage,
        @JsonProperty(value = "consumables", access = JsonProperty.Access.WRITE_ONLY) List<Option> legacyConsumables) {

    public static final int MAX_AUGMENTS = 6;

    public ArenaBuildData {
        coreBuilds = List.copyOf(coreBuilds);
        skillOrders = skillOrders == null ? null : List.copyOf(skillOrders);
        prismatics = List.copyOf(prismatics);
        legacyConsumables = legacyConsumables == null ? null : List.copyOf(legacyConsumables);
        augments = copyAugments(augments);
    }

    public ArenaBuildData(long games, long wins, List<Core> coreBuilds,
            List<List<Option>> augments, List<Option> prismatics, Coverage coverage) {
        this(games, wins, coreBuilds, List.of(), augments, prismatics, coverage, null);
    }

    public ArenaBuildData(long games, long wins, List<Core> coreBuilds,
            List<List<Option>> augments, List<Option> prismatics, Coverage coverage,
            List<Option> legacyConsumables) {
        this(games, wins, coreBuilds, List.of(), augments, prismatics, coverage, legacyConsumables);
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public double winrate() { return games == 0 ? 0 : (double) wins / games; }

    @JsonPropertyOrder({"id", "items", "matches", "wins", "winrate", "pickrate", "builds", "augments", "prismatics"})
    public record Core(String id, List<Integer> items, long matches, long wins, double pickrate,
            List<FullBuild> builds, List<List<Option>> augments, List<Option> prismatics) {
        public Core {
            items = List.copyOf(items);
            builds = List.copyOf(builds);
            prismatics = List.copyOf(prismatics);
            augments = copyAugments(augments);
        }
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winrate() { return matches == 0 ? 0 : (double) wins / matches; }
    }

    @JsonPropertyOrder({"items", "matches", "wins", "winrate"})
    public record FullBuild(List<Integer> items, long matches, long wins,
            @JsonProperty(value = "consumables", access = JsonProperty.Access.WRITE_ONLY) List<Integer> legacyConsumables) {
        public FullBuild {
            items = List.copyOf(items);
            legacyConsumables = legacyConsumables == null ? null : List.copyOf(legacyConsumables);
        }

        public FullBuild(List<Integer> items, long matches, long wins) {
            this(items, matches, wins, null);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winrate() { return matches == 0 ? 0 : (double) wins / matches; }
    }

    @JsonPropertyOrder({"id", "matches", "wins", "winrate"})
    public record Option(int id, long matches, long wins) {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winrate() { return matches == 0 ? 0 : (double) wins / matches; }
    }

    public record Coverage(long matches, long coreGames, long missingCoreGames,
            long firstPrismaticFallbackGames, long buildTimelineGames) {}

    private static List<List<Option>> copyAugments(List<List<Option>> values) {
        if (values.size() != MAX_AUGMENTS) throw new IllegalArgumentException("Six augment positions required");
        java.util.ArrayList<List<Option>> copy = new java.util.ArrayList<>();
        for (List<Option> position : values) copy.add(List.copyOf(position));
        return List.copyOf(copy);
    }
}
