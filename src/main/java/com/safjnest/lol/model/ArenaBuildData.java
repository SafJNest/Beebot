package com.safjnest.lol.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ArenaBuildData(int schemaVersion, int aggregationVersion, Stats stats,
        List<Core> cores, List<ItemOption> items, List<PrismaticOption> prismatics, List<AugmentOption> augments,
        Coverage coverage) {

    public static final int SCHEMA_VERSION = 5;
    public static final int AGGREGATION_VERSION = 5;
    public static final int MAX_AUGMENTS = 6;

    public ArenaBuildData {
        cores = List.copyOf(cores);
        items = List.copyOf(items);
        prismatics = List.copyOf(prismatics);
        augments = List.copyOf(augments);
    }

    public record Stats(long games, long wins) {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winRate() { return games == 0 ? 0 : (double) wins / games; }
    }

    public record Core(int bootsId, int firstPrismaticId, Stats stats, List<ItemOption> items,
            List<PrismaticOption> prismatics, List<AugmentOption> augments) {
        public Core {
            items = List.copyOf(items);
            prismatics = List.copyOf(prismatics);
            augments = List.copyOf(augments);
        }
    }

    public record ItemOption(int position, int itemId, long games, long wins) {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winRate() { return games == 0 ? 0 : (double) wins / games; }
    }

    public record PrismaticOption(int prismaticId, long games, long wins) {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winRate() { return games == 0 ? 0 : (double) wins / games; }
    }

    public record AugmentOption(int augmentId, int position, long games, long wins) {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double winRate() { return games == 0 ? 0 : (double) wins / games; }
    }

    public record Coverage(long matches, long coreGames, long missingCoreGames,
            long firstPrismaticFallbackGames, long buildTimelineGames) {}
}
