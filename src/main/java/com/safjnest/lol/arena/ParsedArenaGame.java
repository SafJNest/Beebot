package com.safjnest.lol.arena;

import java.util.List;

public record ParsedArenaGame(boolean win, Integer bootsId, Integer firstPrismaticId,
        boolean firstPrismaticFallback, List<Integer> prismatics, List<Integer> buildItems,
        List<Augment> augments, boolean buildTimeline, List<Integer> fallbackBuildItems) {

    public ParsedArenaGame {
        prismatics = List.copyOf(prismatics);
        buildItems = List.copyOf(buildItems);
        augments = List.copyOf(augments);
        fallbackBuildItems = List.copyOf(fallbackBuildItems);
    }

    public record Augment(int id, int position) {}
}
