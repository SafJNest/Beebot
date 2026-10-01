package com.safjnest.lol.arena;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Build.Kind;

public record ParsedArenaGame(Integer subTeamPlacement, boolean win, Core core,
        List<Observation> choices, Coverage coverage) {

    public ParsedArenaGame { choices = List.copyOf(choices); }

    public record Core(List<Integer> itemIds) {
        public Core { itemIds = List.copyOf(itemIds); }
    }

    public record Observation(Kind kind, int id, Integer position, Long timestampMillis) {}

    public record Coverage(Map<String, Long> missing, Map<String, Long> ambiguous, Map<String, Long> rejected) {
        public Coverage {
            missing = Map.copyOf(missing);
            ambiguous = Map.copyOf(ambiguous);
            rejected = Map.copyOf(rejected);
        }
    }
}
