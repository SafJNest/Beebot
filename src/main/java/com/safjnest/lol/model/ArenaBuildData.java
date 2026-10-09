package com.safjnest.lol.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.lol.model.statistics.StatisticalLeaf;

public record ArenaBuildData(int schemaVersion, int aggregationVersion, StatisticalLeaf stats,
        Positions positions, List<ObservedBuild> builds, List<Core> cores, Coverage coverage) {

    public static final int SCHEMA_VERSION = 3;
    public static final int AGGREGATION_VERSION = 3;

    public ArenaBuildData {
        builds = List.copyOf(builds);
        cores = List.copyOf(cores);
    }

    public record Positions(List<Choice> boots, List<Slot> augments,
            List<Slot> prismatics, @JsonProperty("items") List<Slot> legendaryItems,
            List<Choice> membership, List<Choice> unpositioned) {
        public Positions {
            boots = List.copyOf(boots);
            augments = List.copyOf(augments);
            prismatics = List.copyOf(prismatics);
            legendaryItems = List.copyOf(legendaryItems);
            membership = List.copyOf(membership);
            unpositioned = List.copyOf(unpositioned);
        }
    }

    public record ObservedBuild(@JsonProperty("boots") int bootsId,
            @JsonProperty("prismatic") int firstPrismaticId, @JsonProperty("items") List<Integer> legendaryIds,
            StatisticalLeaf stats, long denominator, Map<String, Long> orderQuality) {
        public ObservedBuild {
            legendaryIds = List.copyOf(legendaryIds);
            orderQuality = Map.copyOf(orderQuality);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public enum AnchorKind { AUGMENT, PRISMATIC }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CoreKey(@JsonProperty("boots") int bootsId, Integer augment, Integer prismatic) {
        public CoreKey {
            if (bootsId <= 0 || (augment == null) == (prismatic == null)
                    || augment != null && augment <= 0 || prismatic != null && prismatic <= 0)
                throw new IllegalArgumentException("Boots and one core anchor required");
        }

        public CoreKey(int bootsId, AnchorKind kind, int id) {
            this(bootsId, kind == AnchorKind.AUGMENT ? id : null, kind == AnchorKind.PRISMATIC ? id : null);
        }

        @JsonIgnore
        public AnchorKind anchorKind() { return augment != null ? AnchorKind.AUGMENT : AnchorKind.PRISMATIC; }

        @JsonIgnore
        public int anchorId() { return augment != null ? augment : prismatic; }
    }

    public record Core(@JsonProperty("core") CoreKey key, StatisticalLeaf stats, long denominator, List<Step> steps) {
        public Core { steps = List.copyOf(steps); }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public record Step(Context context, StatisticalLeaf stats, Choices choices) {}

    public record Context(@JsonProperty("boots") int bootsId, List<EquipmentKey> equipment, List<AugmentKey> augments) {
        public Context {
            equipment = List.copyOf(equipment);
            augments = List.copyOf(augments);
        }
    }

    public record EquipmentKey(@JsonInclude(JsonInclude.Include.NON_NULL) Integer item,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer prismatic, Integer position, Integer typePosition) {
        public EquipmentKey {
            if ((item == null) == (prismatic == null) || item != null && item <= 0 || prismatic != null && prismatic <= 0)
                throw new IllegalArgumentException("One equipment identity required");
        }

        public EquipmentKey(Build.Kind kind, int id, Integer position, Integer typePosition) {
            this(kind == Build.Kind.ITEM ? id : null, kind == Build.Kind.PRISMATIC ? id : null, position, typePosition);
        }

        @JsonIgnore
        public Build.Kind kind() { return item != null ? Build.Kind.ITEM : Build.Kind.PRISMATIC; }

        @JsonIgnore
        public int id() { return item != null ? item : prismatic; }
    }

    public record AugmentKey(int augment, int position) {
        @JsonIgnore
        public int id() { return augment; }
    }

    public record Choices(@JsonProperty("items") List<Choice> legendary, List<Choice> prismatics,
            List<Slot> augments) {
        public Choices {
            legendary = List.copyOf(legendary);
            prismatics = List.copyOf(prismatics);
            augments = List.copyOf(augments);
        }
    }

    public record Choice(@JsonInclude(JsonInclude.Include.NON_NULL) Integer item,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer boots,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer prismatic,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer augment, Integer position,
            StatisticalLeaf stats, Build.Timing timing, long denominator, long unpositionedGames) {
        public Choice {
            int identities = (item == null ? 0 : 1) + (boots == null ? 0 : 1)
                + (prismatic == null ? 0 : 1) + (augment == null ? 0 : 1);
            if (identities != 1 || item != null && item <= 0 || boots != null && boots <= 0
                    || prismatic != null && prismatic <= 0 || augment != null && augment <= 0)
                throw new IllegalArgumentException("One choice identity required");
        }

        public static Choice of(Build.Kind kind, int id, Integer position, StatisticalLeaf stats,
                Build.Timing timing, long denominator, long unpositionedGames) {
            return new Choice(kind == Build.Kind.ITEM ? id : null, kind == Build.Kind.BOOTS ? id : null,
                kind == Build.Kind.PRISMATIC ? id : null, kind == Build.Kind.AUGMENT ? id : null,
                position, stats, timing, denominator, unpositionedGames);
        }

        @JsonIgnore
        public Build.Kind kind() {
            if (item != null) return Build.Kind.ITEM;
            if (boots != null) return Build.Kind.BOOTS;
            return prismatic != null ? Build.Kind.PRISMATIC : Build.Kind.AUGMENT;
        }

        @JsonIgnore
        public int id() {
            if (item != null) return item;
            if (boots != null) return boots;
            return prismatic != null ? prismatic : augment;
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public record Slot(int position, StatisticalLeaf population, List<Choice> options) {
        public Slot { options = List.copyOf(options); }
    }

    public record Coverage(long matches, long participantGames, long decisionCoreGames, long fallbackCoreGames,
            long observedBuildGames, Map<String, Long> missing, Map<String, Long> ambiguous,
            Map<String, Long> rejected, Map<String, Long> firstPrismaticResolutionTypes,
            Map<String, Long> equipmentOrder, Map<String, Long> firstPrismaticQuality) {
        public Coverage {
            missing = Map.copyOf(missing);
            ambiguous = Map.copyOf(ambiguous);
            rejected = Map.copyOf(rejected);
            firstPrismaticResolutionTypes = Map.copyOf(firstPrismaticResolutionTypes);
            equipmentOrder = Map.copyOf(equipmentOrder);
            firstPrismaticQuality = Map.copyOf(firstPrismaticQuality);
        }
    }
}
