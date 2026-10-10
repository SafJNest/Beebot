package com.safjnest.lol.arena;

import java.util.List;

public record ParsedArenaGame(boolean win, Integer bootsId, Integer firstPrismaticId,
        boolean firstPrismaticFallback, List<Integer> prismatics, List<Integer> legendaryItems,
        List<Augment> augments, boolean buildTimeline) {

    public ParsedArenaGame {
        prismatics = List.copyOf(prismatics);
        legendaryItems = List.copyOf(legendaryItems);
        augments = List.copyOf(augments);
    }

    public record Augment(int id, int position) {}
}
