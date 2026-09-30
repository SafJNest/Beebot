package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.safjnest.lol.champion.RuneSignature;
import com.safjnest.lol.champion.BuildSignature;
import com.safjnest.lol.champion.ChampionBuildData;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.utils.ChampionBuildTimelineUtils;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ChampionBuildEngineTest {

    @Test
    public void aggregatesRuneSetupsAndKeepsTheBestCompleteShardCombination() {
        ChampionBuildEngine.RuneOptionAccumulator runes = new ChampionBuildEngine.RuneOptionAccumulator();
        RuneSignature first = runeSignature(List.of(5008, 5008, 5011));
        RuneSignature second = runeSignature(List.of(5008, 5010, 5011));

        runes.add(first, false);
        runes.add(first, false);
        runes.add(second, true);
        runes.add(second, true);
        runes.add(runeSignature(List.of(5008, 5010)), true);

        List<Build.RuneOption> result = runes.toOptions(5);

        assertEquals(1, result.size());
        assertEquals(first.toKey(), result.get(0).id());
        assertEquals(5, result.get(0).matches());
        assertEquals(3, result.get(0).wins());
        assertEquals(0.6, result.get(0).winrate(), 0.0001);
        assertEquals(second.statShards(), result.get(0).configuration().statShards());
    }

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

    @Test
    public void clearsRuneAndSkillOrderAccumulators() {
        ChampionBuildEngine.RuneOptionAccumulator runes = new ChampionBuildEngine.RuneOptionAccumulator();
        runes.add(runeSignature(List.of(5008, 5008, 5011)), true);
        runes.clear();

        ChampionBuildEngine.SkillOrderTrie orders = new ChampionBuildEngine.SkillOrderTrie();
        orders.add(List.of(1, 2, 3), true);
        orders.clear();

        assertEquals(0, runes.toOptions(1).size());
        assertEquals(0, orders.toOptions(1).size());
    }

    @Test
    public void aggregatesValidTimelineBuildsWhenSomeEventsCannotBeAttributed() {
        ChampionBuildEngine.BuildAccumulator accumulator = ChampionBuildEngine.newAccumulator(null);
        ChampionBuildEngine.accept(accumulator, game(100000, 300000));
        ChampionBuildEngine.accept(accumulator, game(700000, 500000));
        ChampionBuildEngine.accept(accumulator, game(0, 0));

        Build build = ChampionBuildEngine.finish(accumulator).get(0);

        Build.CoreBuildOption core = build.coreBuilds().get(0);
        assertEquals(3, core.matches());
        assertEquals(2, core.timedMatches().intValue());
        assertEquals(500, core.averageCompletionTimeSeconds(), 0.001);
        Build.Option firstCoreItem = build.coreItems().stream().filter(option -> option.id().equals("3078")).findFirst().orElseThrow();
        assertEquals(3, firstCoreItem.matches());
        assertEquals(2, firstCoreItem.timedMatches().intValue());
        assertEquals(400, firstCoreItem.averagePurchaseTimeSeconds(), 0.001);
        Build.Option boots = build.bootOptions().get(0);
        assertEquals(3, boots.matches());
        assertEquals(2, boots.timedMatches().intValue());
        assertEquals(800, boots.averagePurchaseTimeSeconds(), 0.001);
        Build.Option supportItem = build.supportItemOptions().get(0);
        assertEquals(3, supportItem.matches());
        assertEquals(2, supportItem.timedMatches().intValue());
        assertEquals(50, supportItem.averagePurchaseTimeSeconds(), 0.001);
        assertEquals("3871", build.roleBoundItemOptions().get(0).id());
        assertEquals(3, build.roleBoundItemOptions().get(0).matches());
        assertEquals(2, build.roleBoundItemOptions().get(0).timedMatches().intValue());
        assertEquals(2, build.skillOrders().get(0).timedOrder().size());
        assertEquals(1, build.skillOrders().get(0).timedOrder().get(0).level().intValue());
        assertEquals(60, build.skillOrders().get(0).timedOrder().get(0).averageUpgradeTimeSeconds(), 0.001);
        String json = build.toJson();
        assertTrue(json.contains("averagePurchaseTimeSeconds"));
        assertTrue(json.contains("averageCompletionTimeSeconds"));
        assertTrue(json.contains("timedOrder"));
        assertEquals(build, Build.fromJson(json));
    }

    @Test
    public void includesCoreBuildWhenBootsAreAbsent() {
        BuildSignature signature = new BuildSignature(List.of(), 0, 0, List.of(3078, 3031),
            List.of(3078, 3031), sequence(18, 1, 2, 3), List.of(), List.of(), List.of());
        ChampionBuildData.Game game = new ChampionBuildData.Game(signature, null, true, 0,
            java.util.Map.of(), List.of());
        ChampionBuildEngine.BuildAccumulator accumulator = ChampionBuildEngine.newAccumulator(null);

        ChampionBuildEngine.accept(accumulator, game);

        Build build = ChampionBuildEngine.finish(accumulator).get(0);
        assertEquals(1, build.games());
        assertTrue(build.bootOptions().isEmpty());
        assertEquals(1, build.coreBuilds().get(0).matches());
    }

    private static ChampionBuildData.Game game(long firstCoreTime, long secondCoreTime) {
        BuildSignature signature = new BuildSignature(List.of(), 3006, 3871, List.of(3078, 3031),
            List.of(3078, 3031), List.of(1, 2), List.of(), List.of(), List.of(4, 7));
        java.util.Map<Integer, Long> itemTimes = firstCoreTime == 0 ? java.util.Map.of() : java.util.Map.of(
            3078, firstCoreTime, 3031, secondCoreTime, 3006, 800000L, 3871, 50000L);
        List<ChampionBuildTimelineUtils.SkillUpgrade> upgrades = firstCoreTime == 0 ? List.of() : List.of(
            new ChampionBuildTimelineUtils.SkillUpgrade(1, 1, 60000),
            new ChampionBuildTimelineUtils.SkillUpgrade(2, 2, 120000));
        return new ChampionBuildData.Game(signature, null, true, 3871, itemTimes, upgrades);
    }

    private static List<Integer> sequence(int length, int... pattern) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < length; index++) result.add(pattern[index % pattern.length]);
        return result;
    }

    private static RuneSignature runeSignature(List<Integer> statShards) {
        return new RuneSignature(8000, 8005, List.of(9104, 8014, 8299), 8400, List.of(8444, 8451), statShards);
    }
}
