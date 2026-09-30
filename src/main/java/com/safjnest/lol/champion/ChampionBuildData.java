package com.safjnest.lol.champion;

import com.safjnest.lol.utils.ChampionBuildTimelineUtils;

import java.util.List;
import java.util.Map;

public final class ChampionBuildData {

    public record Game(BuildSignature signature, RuneSignature runes, boolean win, int roleBoundId,
                       Map<Integer, Long> itemPurchaseTimesMillis,
                       List<ChampionBuildTimelineUtils.SkillUpgrade> skillUpgrades) {}

    private ChampionBuildData() {}
}
