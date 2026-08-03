package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;

import com.safjnest.lol.model.Build;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ChampionBuildEngineTest {

    @Test
    public void shorterPrefixSupportsTheCompatibleCompleteOrder() {
        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        List<Integer> complete = sequence(18, 1, 2, 3, 1, 1, 4);
        orders.add(complete, true);
        orders.add(List.of(1, 2, 3, 1), false);

        List<Build.SkillOrderOption> result = orders.toOptions(2);

        assertEquals(1, result.size());
        assertEquals(complete, result.get(0).order());
        assertEquals(2, result.get(0).matches());
        assertEquals(1, result.get(0).wins());
    }

    @Test
    public void fallsBackToLevelSeventeenWhenNoCompleteOrderExists() {
        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        List<Integer> levelSeventeen = sequence(17, 1, 2, 3);
        orders.add(levelSeventeen, true);
        orders.add(sequence(16, 1, 2, 3), false);

        List<Build.SkillOrderOption> result = orders.toOptions(2);

        assertEquals(1, result.size());
        assertEquals(levelSeventeen, result.get(0).order());
        assertEquals(17, result.get(0).order().size());
        assertEquals(2, result.get(0).matches());
    }

    @Test
    public void fallsBackToLevelSixteenWhenNoLongerOrderExists() {
        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        List<Integer> levelSixteen = sequence(16, 1, 2, 3);
        orders.add(levelSixteen, true);
        orders.add(sequence(15, 1, 2, 3), false);

        List<Build.SkillOrderOption> result = orders.toOptions(2);

        assertEquals(1, result.size());
        assertEquals(levelSixteen, result.get(0).order());
        assertEquals(16, result.get(0).order().size());
        assertEquals(2, result.get(0).matches());
    }

    @Test
    public void paddingEndsTheObservedOrder() {
        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        orders.add(List.of(1, 2, 0, 3), true);

        List<Build.SkillOrderOption> result = orders.toOptions(1);

        assertEquals(1, result.size());
        assertEquals(List.of(1, 2), result.get(0).order());
    }

    @Test
    public void treatsTheFourthAbilitySlotLikeEveryOtherAbility() {
        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        List<Integer> order = List.of(1, 2, 3, 4, 4, 4, 4, 4, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1);
        orders.add(order, true);

        List<Build.SkillOrderOption> result = orders.toOptions(1);

        assertEquals(order, result.get(0).order());
    }

    @Test
    public void ordersCandidatesByPrefixSupportThenExactGames() {
        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        List<Integer> first = sequence(18, 1, 2, 3);
        List<Integer> second = sequence(18, 2, 3, 1);
        orders.add(first, true);
        orders.add(List.of(1, 2, 3), false);
        orders.add(second, true);

        List<Build.SkillOrderOption> result = orders.toOptions(3);

        assertEquals(first, result.get(0).order());
        assertEquals(2, result.get(0).matches());
        assertEquals(second, result.get(1).order());
    }

    private static List<Integer> sequence(int length, int... pattern) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < length; index++) result.add(pattern[index % pattern.length]);
        return result;
    }
}
