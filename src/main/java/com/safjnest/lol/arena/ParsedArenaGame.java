package com.safjnest.lol.arena;

import java.util.List;

import com.safjnest.lol.utils.ChampionBuildTimelineUtils.SkillUpgrade;

public record ParsedArenaGame(boolean win, Integer bootsId, Integer firstPrismaticId,
        boolean firstPrismaticFallback, List<Integer> prismatics, List<Integer> buildItems,
        List<Augment> augments, boolean buildTimeline, List<Integer> fallbackBuildItems,
        List<Integer> skillOrder, List<SkillUpgrade> skillUpgrades) {

    public ParsedArenaGame {
        prismatics = List.copyOf(prismatics);
        buildItems = List.copyOf(buildItems);
        augments = List.copyOf(augments);
        fallbackBuildItems = List.copyOf(fallbackBuildItems);
        skillOrder = skillOrder == null ? List.of() : List.copyOf(skillOrder);
        skillUpgrades = skillUpgrades == null ? List.of() : List.copyOf(skillUpgrades);
    }

    public record Augment(int id, int position) {}
}
