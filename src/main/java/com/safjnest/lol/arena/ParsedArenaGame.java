package com.safjnest.lol.arena;

import java.util.List;

public record ParsedArenaGame(boolean win, Integer bootsId, Integer firstPrismaticId,
        boolean firstPrismaticFallback, List<Integer> prismatics, List<Integer> consumables,
        List<Integer> legendaryItems,
        List<Augment> augments, boolean buildTimeline, List<Integer> finalItems) {

    public ParsedArenaGame {
        prismatics = List.copyOf(prismatics);
        consumables = List.copyOf(consumables);
        legendaryItems = List.copyOf(legendaryItems);
        augments = List.copyOf(augments);
        finalItems = List.copyOf(finalItems);
    }

    public record Augment(int id, int position) {}
}
