package com.safjnest.lol.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.lol.model.statistics.StatisticalLeaf;

public record ArenaBuildData(int schemaVersion, int aggregationVersion, StatisticalLeaf stats,
        Positions positions, List<ObservedBuild> builds, List<Core> cores, Coverage coverage) {

    public ArenaBuildData {
        builds = List.copyOf(builds);
        cores = List.copyOf(cores);
    }

    public record Positions(List<Build.Choice> boots, List<Build.Slot> augments,
            List<Build.Slot> prismatics, List<Build.Slot> legendaryItems,
            List<Build.Choice> membership, List<Build.Choice> unpositioned) {
        public Positions {
            boots = List.copyOf(boots);
            augments = List.copyOf(augments);
            prismatics = List.copyOf(prismatics);
            legendaryItems = List.copyOf(legendaryItems);
            membership = List.copyOf(membership);
            unpositioned = List.copyOf(unpositioned);
        }
    }

    public record ObservedBuild(int bootsId, int firstPrismaticId, List<Integer> legendaryIds,
            StatisticalLeaf stats, long denominator, Map<String, Long> orderQuality) {
        public ObservedBuild {
            legendaryIds = List.copyOf(legendaryIds);
            orderQuality = Map.copyOf(orderQuality);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public enum AnchorKind { AUGMENT, PRISMATIC }

    public record CoreKey(int bootsId, AnchorKind anchorKind, int anchorId) {}

    public record Core(CoreKey key, StatisticalLeaf stats, long denominator, List<Step> steps) {
        public Core { steps = List.copyOf(steps); }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public record Step(Context context, StatisticalLeaf stats, Choices choices) {}

    public record Context(int bootsId, List<EquipmentKey> equipment, List<AugmentKey> augments) {
        public Context {
            equipment = List.copyOf(equipment);
            augments = List.copyOf(augments);
        }
    }

    public record EquipmentKey(Build.Kind kind, int id, Integer position, Integer typePosition) {}

    public record AugmentKey(int id, int position) {}

    public record Choices(List<Build.Choice> legendary, List<Build.Choice> prismatics,
            List<Build.Slot> augments) {
        public Choices {
            legendary = List.copyOf(legendary);
            prismatics = List.copyOf(prismatics);
            augments = List.copyOf(augments);
        }
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
