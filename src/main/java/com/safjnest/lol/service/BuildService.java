package com.safjnest.lol.service;

import com.safjnest.lol.champion.BuildSignature;
import com.safjnest.lol.champion.ChampionBuildData;
import com.safjnest.lol.champion.ChampionBuildProvider;
import com.safjnest.lol.champion.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.nosql.MongoDB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuildService {

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final int SLOT_COUNT = 4;
    private static final int AUGMENT_SLOT_COUNT = 4;

    private BuildService() {}

    public static List<Build> getAll(Filter filter) {
        return loadBuilds(filter, true);
    }

    public static Build getAggregate(Filter filter) {
        return getAggregate(filter, true);
    }

    public static boolean hasStored(Filter filter) {
        return filter != null && !loadBuilds(filter, false).isEmpty();
    }

    public static List<Build> recomputeAll(Filter filter) {
        long started = System.nanoTime();
        List<Build> computed = computeAll(filter);
        if (computed != null && !computed.isEmpty()) {
            long persistenceStarted = System.nanoTime();
            MongoDB.upsertChampionBuilds(computed);
            System.out.println("build persistence completed: builds=" + computed.size()
                + ", persistenceMs=" + millis(System.nanoTime() - persistenceStarted)
                + ", totalMs=" + millis(System.nanoTime() - started));
        }
        return computed;
    }

    // ============================================================================

    static Build getAggregate(Filter filter, boolean allowCompute) {
        if (filter == null) return null;
        List<Build> builds = loadBuilds(filter, allowCompute);
        return builds.isEmpty() ? null : builds.get(0);
    }

    private static List<Build> loadBuilds(Filter filter, boolean allowCompute) {
        if (filter == null) return List.of();

        List<Build> stored;
        try {
            stored = MongoDB.findChampionBuilds(filter);
        } catch (RuntimeException exception) {
            if (!allowCompute) return List.of();
            throw exception;
        }
        if (stored != null && !stored.isEmpty()) return stored;
        if (!allowCompute) return List.of();

        List<Build> computed = computeAll(filter);
        if (computed != null && !computed.isEmpty()) computed.forEach(MongoDB::upsertChampionBuild);
        return computed == null ? List.of() : computed;
    }

    private static List<Build> computeAll(Filter filter) {
        Map<String, int[]> coreBuilds = new LinkedHashMap<>();
        Map<String, List<Integer>> coreBuildItems = new LinkedHashMap<>();
        Map<Integer, int[]> coreItems = new LinkedHashMap<>();
        Map<String, int[]> starters = new LinkedHashMap<>();
        Map<Integer, int[]> boots = new LinkedHashMap<>();
        Map<Integer, int[]> supportItems = new LinkedHashMap<>();
        Map<Integer, Map<Integer, int[]>> slots = new LinkedHashMap<>();
        Map<String, int[]> runes = new LinkedHashMap<>();
        Map<String, RuneSignature> runeConfigurations = new LinkedHashMap<>();
        Map<String, int[]> summonerSpells = new LinkedHashMap<>();
        Map<String, int[]> skillOrders = new LinkedHashMap<>();
        Map<Integer, int[]> prismatics = new LinkedHashMap<>();
        Map<Integer, Map<Integer, int[]>> augments = new LinkedHashMap<>();

        long started = System.nanoTime();
        System.out.println("build compute started");
        int[] totals = new int[3];
        long sourceStarted = System.nanoTime();
        ChampionBuildProvider.forEach(filter, record -> {
            totals[0]++;
            ChampionBuildData.Game game = ChampionBuildProvider.parse(record, filter);
            if (game == null) return;

            totals[1]++;
            if (game.win()) totals[2]++;
            addCore(game, coreBuilds, coreBuildItems, coreItems);
            addStarter(game, starters);
            addBoots(game, boots);
            addSupportItem(game, supportItems);
            addSlots(game, slots);
            addRunes(game, runes, runeConfigurations);
            addSummonerSpells(game, summonerSpells);
            addSkillOrder(game, skillOrders);
            addPrismatics(game, prismatics);
            addAugments(game, augments);
        });
        long sourceNanos = System.nanoTime() - sourceStarted;
        int games = totals[1];
        int wins = totals[2];
        if (games == 0) {
            System.out.println("build compute completed: records=" + totals[0]
                + ", games=0, wins=0, sourceMs=" + millis(sourceNanos)
                + ", totalMs=" + millis(System.nanoTime() - started));
            return List.of();
        }

        Build aggregate = new Build(
            filter,
            games,
            wins,
            rate(wins, games),
            toCoreBuilds(coreBuilds, coreBuildItems, games),
            toOptions(coreItems, games),
            toConfigOptions(starters, games),
            toOptions(boots, games),
            toOptions(supportItems, games),
            toSlots(slots, SLOT_COUNT, games),
            toRuneOptions(runes, runeConfigurations, games),
            toConfigOptions(summonerSpells, games),
            toSkillOrders(skillOrders, games),
            toOptions(prismatics, games),
            toSlots(augments, AUGMENT_SLOT_COUNT, games)
        );
        System.out.println("build compute completed: records=" + totals[0] + ", games=" + games
            + ", wins=" + wins + ", sourceMs=" + millis(sourceNanos)
            + ", totalMs=" + millis(System.nanoTime() - started));
        return List.of(aggregate);
    }

    private static long millis(long nanos) {
        return nanos / NANOS_PER_MILLI;
    }

    private static void addCore(ChampionBuildData.Game game, Map<String, int[]> builds,
                                Map<String, List<Integer>> buildItems, Map<Integer, int[]> items) {
        BuildSignature signature = game.signature();
        String key = BuildUtils.joinInts(signature.core());
        add(builds, key, game.win());
        buildItems.putIfAbsent(key, signature.core());
        for (Integer item : signature.core()) add(items, item, game.win());
    }

    private static void addStarter(ChampionBuildData.Game game, Map<String, int[]> values) {
        add(values, BuildUtils.joinInts(game.signature().starter()), game.win());
    }

    private static void addBoots(ChampionBuildData.Game game, Map<Integer, int[]> values) {
        if (game.signature().boots() != 0) add(values, game.signature().boots(), game.win());
    }

    private static void addSupportItem(ChampionBuildData.Game game, Map<Integer, int[]> values) {
        if (game.signature().suppItem() != 0) add(values, game.signature().suppItem(), game.win());
    }

    private static void addSlots(ChampionBuildData.Game game, Map<Integer, Map<Integer, int[]>> values) {
        BuildSignature signature = game.signature();
        Set<Integer> excluded = coreExcluded(signature);
        List<Integer> extra = new ArrayList<>();
        for (Integer item : signature.fullBuild())
            if (!excluded.contains(item) && !signature.starter().contains(item)) extra.add(item);
        for (int slot = 0; slot < SLOT_COUNT && slot < extra.size(); slot++)
            add(values, slot, extra.get(slot), game.win());
    }

    private static void addRunes(ChampionBuildData.Game game, Map<String, int[]> values,
                                 Map<String, RuneSignature> configurations) {
        RuneSignature configuration = game.runes();
        if (configuration == null) return;
        String key = configuration.toKey();
        add(values, key, game.win());
        configurations.putIfAbsent(key, configuration);
    }

    private static void addSummonerSpells(ChampionBuildData.Game game, Map<String, int[]> values) {
        if (!game.signature().summonerSpells().isEmpty())
            add(values, BuildUtils.joinInts(game.signature().summonerSpells()), game.win());
    }

    private static void addSkillOrder(ChampionBuildData.Game game, Map<String, int[]> values) {
        if (!game.signature().spellOrder().isEmpty())
            add(values, BuildUtils.joinInts(game.signature().spellOrder()), game.win());
    }

    private static void addPrismatics(ChampionBuildData.Game game, Map<Integer, int[]> values) {
        for (Integer item : game.signature().prismatics()) add(values, item, game.win());
    }

    private static void addAugments(ChampionBuildData.Game game, Map<Integer, Map<Integer, int[]>> values) {
        List<Integer> augmentIds = game.signature().augments();
        for (int slot = 0; slot < AUGMENT_SLOT_COUNT && slot < augmentIds.size(); slot++)
            add(values, slot, augmentIds.get(slot), game.win());
    }

    private static List<Build.CoreBuildOption> toCoreBuilds(Map<String, int[]> values,
                                                              Map<String, List<Integer>> items,
                                                              int totalGames) {
        List<Build.CoreBuildOption> result = new ArrayList<>();
        List<Map.Entry<String, int[]>> entries = sortedByGames(values, String::compareTo);
        for (Map.Entry<String, int[]> entry : entries) {
            int matches = entry.getValue()[0];
            int wins = entry.getValue()[1];
            result.add(new Build.CoreBuildOption(entry.getKey(),
                items.getOrDefault(entry.getKey(), parseIds(entry.getKey())), matches, wins,
                rate(wins, matches), rate(matches, totalGames)));
        }
        return result;
    }

    private static List<Build.Option> toOptions(Map<Integer, int[]> values, int totalGames) {
        List<Build.Option> result = new ArrayList<>();
        List<Map.Entry<Integer, int[]>> entries = sortedByGames(values, Integer::compareTo);
        for (Map.Entry<Integer, int[]> entry : entries)
            result.add(option(String.valueOf(entry.getKey()), entry.getValue(), totalGames));
        return result;
    }

    private static List<Build.Option> toConfigOptions(Map<String, int[]> values, int totalGames) {
        List<Build.Option> result = new ArrayList<>();
        List<Map.Entry<String, int[]>> entries = sortedByGames(values, String::compareTo);
        for (Map.Entry<String, int[]> entry : entries)
            result.add(option(entry.getKey(), entry.getValue(), totalGames));
        return result;
    }

    private static List<List<Build.Option>> toSlots(Map<Integer, Map<Integer, int[]>> values,
                                                     int count, int totalGames) {
        List<List<Build.Option>> result = new ArrayList<>();
        for (int slot = 0; slot < count; slot++)
            result.add(toOptions(values.getOrDefault(slot, Map.of()), totalGames));
        return result;
    }

    private static List<Build.RuneOption> toRuneOptions(Map<String, int[]> values,
                                                         Map<String, RuneSignature> configurations,
                                                         int totalGames) {
        List<Build.RuneOption> result = new ArrayList<>();
        List<Map.Entry<String, int[]>> entries = sortedByGames(values, String::compareTo);
        for (Map.Entry<String, int[]> entry : entries) {
            RuneSignature signature = configurations.get(entry.getKey());
            if (signature == null) continue;
            int matches = entry.getValue()[0];
            int wins = entry.getValue()[1];
            result.add(new Build.RuneOption(entry.getKey(), signature, matches, wins,
                rate(wins, matches), rate(matches, totalGames)));
        }
        return result;
    }

    private static List<Build.SkillOrderOption> toSkillOrders(Map<String, int[]> values, int totalGames) {
        List<Build.SkillOrderOption> result = new ArrayList<>();
        List<Map.Entry<String, int[]>> entries = sortedByGames(values, String::compareTo);
        for (Map.Entry<String, int[]> entry : entries) {
            int matches = entry.getValue()[0];
            int wins = entry.getValue()[1];
            result.add(new Build.SkillOrderOption(entry.getKey(), parseIds(entry.getKey()), matches, wins,
                rate(wins, matches), rate(matches, totalGames)));
        }
        return result;
    }

    private static Build.Option option(String id, int[] stats, int totalGames) {
        int matches = stats[0];
        int wins = stats[1];
        return new Build.Option(id, matches, wins, rate(wins, matches), rate(matches, totalGames));
    }

    private static Set<Integer> coreExcluded(BuildSignature signature) {
        Set<Integer> excluded = new HashSet<>(signature.core());
        excluded.add(signature.boots());
        excluded.add(signature.suppItem());
        excluded.addAll(signature.prismatics());
        excluded.addAll(signature.augments());
        return excluded;
    }

    private static <K> List<Map.Entry<K, int[]>> sortedByGames(Map<K, int[]> values,
                                                                Comparator<? super K> comparator) {
        List<Map.Entry<K, int[]>> result = new ArrayList<>(values.entrySet());
        result.sort((left, right) -> {
            int matches = Integer.compare(right.getValue()[0], left.getValue()[0]);
            return matches != 0 ? matches : comparator.compare(left.getKey(), right.getKey());
        });
        return result;
    }

    private static void add(Map<Integer, int[]> values, int key, boolean win) {
        int[] stats = values.computeIfAbsent(key, ignored -> new int[2]);
        stats[0]++;
        if (win) stats[1]++;
    }

    private static void add(Map<String, int[]> values, String key, boolean win) {
        if (key == null || key.isBlank()) return;
        int[] stats = values.computeIfAbsent(key, ignored -> new int[2]);
        stats[0]++;
        if (win) stats[1]++;
    }

    private static void add(Map<Integer, Map<Integer, int[]>> values, int slot, int key, boolean win) {
        add(values.computeIfAbsent(slot, ignored -> new LinkedHashMap<>()), key, win);
    }

    private static List<Integer> parseIds(String key) {
        if (key == null || key.isBlank()) return List.of();
        try { return BuildUtils.parseDashList(key); }
        catch (RuntimeException ignored) { return List.of(); }
    }

    private static double rate(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0;
    }
}
