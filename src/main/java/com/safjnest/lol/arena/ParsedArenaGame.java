package com.safjnest.lol.arena;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Build.Kind;

public record ParsedArenaGame(Integer subTeamPlacement, boolean win, Integer boots,
        List<Equipment> equipment, List<Augment> augments, List<Observation> choices,
        Integer firstPrismaticId, FirstPrismaticResult firstPrismatic, boolean firstPrismaticExact,
        boolean equipmentComplete, OrderQuality orderQuality, Coverage coverage) {

    public ParsedArenaGame {
        equipment = List.copyOf(equipment);
        augments = List.copyOf(augments);
        choices = List.copyOf(choices);
    }

    public enum OrderSource { TIMELINE, FIRST_PRISMATIC_RESOLVER, TOOLTIP_FALLBACK }

    public enum OrderQuality { EXACT, MIXED, FALLBACK, UNRESOLVED }

    public record Equipment(Kind kind, int id, Integer position, Integer typePosition,
            Long timestampMillis, Integer tooltipSlot, OrderSource orderSource) {}

    public record Augment(int id, int position) {}

    public record Observation(Kind kind, int id, Integer position, Long timestampMillis) {}

    public record Coverage(Map<String, Long> missing, Map<String, Long> ambiguous, Map<String, Long> rejected) {
        public Coverage {
            missing = Map.copyOf(missing);
            ambiguous = Map.copyOf(ambiguous);
            rejected = Map.copyOf(rejected);
        }
    }
}
