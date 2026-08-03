package com.safjnest.lol.service;

import com.safjnest.lol.champion.BuildSignature;
import com.safjnest.lol.champion.ChampionBuildData;
import com.safjnest.lol.champion.ChampionBuildProvider;
import com.safjnest.lol.champion.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChampionBuildEngine {

    private static final int SLOT_COUNT = 4;
    private static final int AUGMENT_SLOT_COUNT = 4;
    private static final int MAX_ABILITY_SLOT = 4;

    private ChampionBuildEngine() {}

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
        if (filter == null) return List.of();
        List<Build> computed = computeAll(filter);
        if (computed == null || computed.isEmpty()) computed = emptyResult(filter);
        if (!computed.isEmpty()) MongoDB.upsertChampionBuilds(computed);
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
        if (computed == null || computed.isEmpty()) computed = emptyResult(filter);
        computed.forEach(MongoDB::upsertChampionBuild);
        return computed;
    }

    private static List<Build> computeAll(Filter filter) {
        BuildAccumulator accumulator = newAccumulator(filter);
        ChampionBuildProvider.forEachBatch(filter, batch -> {
            try {
                for (QueryRecord record : batch) accept(accumulator, record);
            } finally {
                batch.clear();
            }
        });
        return finish(accumulator);
    }

    static BuildAccumulator newAccumulator(Filter filter) {
        return new BuildAccumulator(filter);
    }

    static void accept(BuildAccumulator accumulator, QueryRecord record) {
        if (accumulator == null || record == null) return;
        accumulator.totalRecords++;
        ChampionBuildData.Game game = ChampionBuildProvider.parse(record, accumulator.filter);
        if (game == null) return;
        accumulator.games++;
        if (game.win()) accumulator.wins++;
        addCore(game, accumulator.coreBuilds, accumulator.coreBuildItems, accumulator.coreItems);
        addStarter(game, accumulator.starters);
        addBoots(game, accumulator.boots);
        addSupportItem(game, accumulator.supportItems);
        addSlots(game, accumulator.slots);
        addRunes(game, accumulator.runes, accumulator.runeConfigurations);
        addSummonerSpells(game, accumulator.summonerSpells);
        addSkillOrder(game, accumulator.skillOrders);
        addPrismatics(game, accumulator.prismatics);
        addAugments(game, accumulator.augments);
    }

    static List<Build> finish(BuildAccumulator accumulator) {
        if (accumulator == null || accumulator.games == 0) return List.of();
        int games = accumulator.games;
        int wins = accumulator.wins;
        return List.of(new Build(
            accumulator.filter, games, wins, rate(wins, games),
            toCoreBuilds(accumulator.coreBuilds, accumulator.coreBuildItems, games),
            toOptions(accumulator.coreItems, games), toConfigOptions(accumulator.starters, games),
            toOptions(accumulator.boots, games), toOptions(accumulator.supportItems, games),
            toSlots(accumulator.slots, SLOT_COUNT, games),
            toRuneOptions(accumulator.runes, accumulator.runeConfigurations, games),
            toConfigOptions(accumulator.summonerSpells, games), accumulator.skillOrders.toOptions(games),
            toOptions(accumulator.prismatics, games), toSlots(accumulator.augments, AUGMENT_SLOT_COUNT, games)
        ));
    }

    static List<Build> emptyResult(Filter filter) {
        return List.of(new Build(
            filter,
            0,
            0,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        ));
    }

    static final class BuildAccumulator {

        private final Filter filter;
        private final Map<String, int[]> coreBuilds = new LinkedHashMap<>();
        private final Map<String, List<Integer>> coreBuildItems = new LinkedHashMap<>();
        private final Map<Integer, int[]> coreItems = new LinkedHashMap<>();
        private final Map<String, int[]> starters = new LinkedHashMap<>();
        private final Map<Integer, int[]> boots = new LinkedHashMap<>();
        private final Map<Integer, int[]> supportItems = new LinkedHashMap<>();
        private final Map<Integer, Map<Integer, int[]>> slots = new LinkedHashMap<>();
        private final Map<String, int[]> runes = new LinkedHashMap<>();
        private final Map<String, RuneSignature> runeConfigurations = new LinkedHashMap<>();
        private final Map<String, int[]> summonerSpells = new LinkedHashMap<>();
        private final SkillOrderTrie skillOrders = new SkillOrderTrie();
        private final Map<Integer, int[]> prismatics = new LinkedHashMap<>();
        private final Map<Integer, Map<Integer, int[]>> augments = new LinkedHashMap<>();
        private int totalRecords;
        private int games;
        private int wins;

        private BuildAccumulator(Filter filter) {
            this.filter = filter;
        }

        Filter filter() {
            return filter;
        }
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

    private static void addSkillOrder(ChampionBuildData.Game game, SkillOrderTrie values) {
        values.add(game.signature().spellOrder(), game.win());
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

    static final class SkillOrderTrie {

        private final SkillOrderNode root = new SkillOrderNode();

        void add(List<Integer> order, boolean win) {
            if (order == null || order.isEmpty()) return;

            SkillOrderNode current = root;
            int depth = 0;
            for (Integer ability : order) {
                if (ability == null || ability < 1 || ability > MAX_ABILITY_SLOT) break;
                current = current.children.computeIfAbsent(ability, ignored -> new SkillOrderNode());
                depth++;
            }
            if (depth == 0) return;

            current.matches++;
            if (win) current.wins++;
        }

        List<Build.SkillOrderOption> toOptions(int totalGames) {
            int targetDepth = deepestObservedDepth(root, 0);
            if (targetDepth == 0) return List.of();

            List<SkillOrderCandidate> candidates = new ArrayList<>();
            collectCandidates(root, new ArrayList<>(), 0, 0, targetDepth, candidates);
            candidates.sort((left, right) -> {
                int support = Integer.compare(right.matches(), left.matches());
                if (support != 0) return support;
                int exact = Integer.compare(right.exactMatches(), left.exactMatches());
                return exact != 0 ? exact : left.id().compareTo(right.id());
            });

            List<Build.SkillOrderOption> result = new ArrayList<>();
            for (SkillOrderCandidate candidate : candidates)
                result.add(new Build.SkillOrderOption(candidate.id(), candidate.order(), candidate.matches(),
                    candidate.wins(), rate(candidate.wins(), candidate.matches()), rate(candidate.matches(), totalGames)));
            return result;
        }

        private static int deepestObservedDepth(SkillOrderNode node, int depth) {
            int result = node.matches > 0 ? depth : 0;
            for (SkillOrderNode child : node.children.values())
                result = Math.max(result, deepestObservedDepth(child, depth + 1));
            return result;
        }

        private static void collectCandidates(SkillOrderNode node, List<Integer> order, int prefixMatches,
                                              int prefixWins, int targetDepth,
                                              List<SkillOrderCandidate> candidates) {
            int matches = prefixMatches + node.matches;
            int wins = prefixWins + node.wins;
            if (order.size() == targetDepth) {
                if (node.matches > 0) candidates.add(new SkillOrderCandidate(BuildUtils.joinInts(order),
                    List.copyOf(order), matches, wins, node.matches));
                return;
            }

            for (Map.Entry<Integer, SkillOrderNode> entry : node.children.entrySet()) {
                order.add(entry.getKey());
                collectCandidates(entry.getValue(), order, matches, wins, targetDepth, candidates);
                order.remove(order.size() - 1);
            }
        }
    }

    private static final class SkillOrderNode {

        private final Map<Integer, SkillOrderNode> children = new LinkedHashMap<>();
        private int matches;
        private int wins;
    }

    private record SkillOrderCandidate(String id, List<Integer> order, int matches, int wins, int exactMatches) {}
}
