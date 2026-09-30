package com.safjnest.lol.service;

import com.safjnest.lol.champion.BuildSignature;
import com.safjnest.lol.champion.ChampionBuildData;
import com.safjnest.lol.champion.ChampionBuildProvider;
import com.safjnest.lol.champion.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.utils.MatchMemoryUtils;
import com.safjnest.lol.utils.ChampionBuildTimelineUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.log.BotLogger;

import java.util.ArrayList;
import java.util.Comparator;
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

    public static Build getAggregate(Filter filter) {
        return getAggregate(filter, true);
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
                MatchMemoryUtils.release(batch);
            }
        });
        logBuildSummary(accumulator, "build-only");
        return finish(accumulator);
    }

    static BuildAccumulator newAccumulator(Filter filter) {
        return new BuildAccumulator(filter);
    }

    static void accept(BuildAccumulator accumulator, QueryRecord record) {
        if (accumulator == null || record == null) return;
        accumulator.sourceRecords++;
        ChampionBuildData.Game game = ChampionBuildProvider.parse(record, accumulator.filter,
            accumulator.parseRejections);
        if (game == null) return;
        accept(accumulator, game);
    }

    static void logBuildSummary(BuildAccumulator accumulator, String source) {
        if (accumulator == null) return;
        BotLogger.info("[ChampionBuild] source=" + source
            + " filter=" + accumulator.filter.toKey()
            + " sourceRecords=" + accumulator.sourceRecords
            + " acceptedGames=" + accumulator.games
            + " rejectedRecords=" + (accumulator.sourceRecords - accumulator.games)
            + " rejectionReasons=" + accumulator.parseRejections);
    }

    static void accept(BuildAccumulator accumulator, ChampionBuildData.Game game) {
        if (accumulator == null || game == null) return;
        accumulator.games++;
        if (game.win()) accumulator.wins++;
        addCore(game, accumulator.coreBuilds, accumulator.coreBuildItems, accumulator.coreItems);
        addCoreTimes(game, accumulator.coreBuildTimes, accumulator.coreItemTimes);
        addStarter(game, accumulator.starters);
        addTimedItem(game, game.signature().boots(), accumulator.boots, accumulator.bootTimes);
        addTimedItem(game, game.signature().suppItem(), accumulator.supportItems, accumulator.supportTimes);
        addTimedItem(game, game.roleBoundId(), accumulator.roleBoundItems, accumulator.roleBoundTimes);
        addSlots(game, accumulator.slots, accumulator.slotTimes);
        addRunes(game, accumulator.runes);
        addSummonerSpells(game, accumulator.summonerSpells);
        addSkillOrder(game, accumulator.skillOrders);
        addPrismatics(game, accumulator.prismatics, accumulator.prismaticTimes);
        addAugments(game, accumulator.augments);
    }

    static List<Build> finish(BuildAccumulator accumulator) {
        if (accumulator == null || accumulator.games == 0) return List.of();
        int games = accumulator.games;
        int wins = accumulator.wins;
        return List.of(new Build(
            accumulator.filter, games, wins, rate(wins, games),
            toCoreBuilds(accumulator.coreBuilds, accumulator.coreBuildItems, accumulator.coreBuildTimes, games),
            toTimedOptions(accumulator.coreItems, accumulator.coreItemTimes, games), toConfigOptions(accumulator.starters, games),
            toTimedOptions(accumulator.boots, accumulator.bootTimes, games),
            toTimedOptions(accumulator.supportItems, accumulator.supportTimes, games),
            toTimedOptions(accumulator.roleBoundItems, accumulator.roleBoundTimes, games),
            toSlots(accumulator.slots, accumulator.slotTimes, SLOT_COUNT, games),
            accumulator.runes.toOptions(games),
            toConfigOptions(accumulator.summonerSpells, games), accumulator.skillOrders.toOptions(games),
            toTimedOptions(accumulator.prismatics, accumulator.prismaticTimes, games),
            toSlots(accumulator.augments, AUGMENT_SLOT_COUNT, games)
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
            List.of(),
            List.of()
        ));
    }

    static final class BuildAccumulator {

        private final Filter filter;
        private final Map<String, int[]> coreBuilds = new LinkedHashMap<>();
        private final Map<String, List<Integer>> coreBuildItems = new LinkedHashMap<>();
        private final Map<String, TimingStats> coreBuildTimes = new LinkedHashMap<>();
        private final Map<Integer, int[]> coreItems = new LinkedHashMap<>();
        private final Map<Integer, TimingStats> coreItemTimes = new LinkedHashMap<>();
        private final Map<String, int[]> starters = new LinkedHashMap<>();
        private final Map<Integer, int[]> boots = new LinkedHashMap<>();
        private final Map<Integer, TimingStats> bootTimes = new LinkedHashMap<>();
        private final Map<Integer, int[]> supportItems = new LinkedHashMap<>();
        private final Map<Integer, TimingStats> supportTimes = new LinkedHashMap<>();
        private final Map<Integer, int[]> roleBoundItems = new LinkedHashMap<>();
        private final Map<Integer, TimingStats> roleBoundTimes = new LinkedHashMap<>();
        private final Map<Integer, Map<Integer, int[]>> slots = new LinkedHashMap<>();
        private final Map<Integer, Map<Integer, TimingStats>> slotTimes = new LinkedHashMap<>();
        private final RuneOptionAccumulator runes = new RuneOptionAccumulator();
        private final Map<String, int[]> summonerSpells = new LinkedHashMap<>();
        private final SkillOrderTrie skillOrders = new SkillOrderTrie();
        private final Map<Integer, int[]> prismatics = new LinkedHashMap<>();
        private final Map<Integer, TimingStats> prismaticTimes = new LinkedHashMap<>();
        private final Map<Integer, Map<Integer, int[]>> augments = new LinkedHashMap<>();
        private final Map<String, Integer> parseRejections = new LinkedHashMap<>();
        private int sourceRecords;
        private int games;
        private int wins;

        private BuildAccumulator(Filter filter) {
            this.filter = filter;
        }

        Filter filter() {
            return filter;
        }

        void clear() {
            MatchMemoryUtils.release(coreBuilds);
            MatchMemoryUtils.release(coreBuildItems);
            MatchMemoryUtils.release(coreBuildTimes);
            MatchMemoryUtils.release(coreItems);
            MatchMemoryUtils.release(coreItemTimes);
            MatchMemoryUtils.release(starters);
            MatchMemoryUtils.release(boots);
            MatchMemoryUtils.release(bootTimes);
            MatchMemoryUtils.release(supportItems);
            MatchMemoryUtils.release(supportTimes);
            MatchMemoryUtils.release(roleBoundItems);
            MatchMemoryUtils.release(roleBoundTimes);
            MatchMemoryUtils.release(slots);
            MatchMemoryUtils.release(slotTimes);
            runes.clear();
            MatchMemoryUtils.release(summonerSpells);
            skillOrders.clear();
            MatchMemoryUtils.release(prismatics);
            MatchMemoryUtils.release(prismaticTimes);
            MatchMemoryUtils.release(augments);
            MatchMemoryUtils.release(parseRejections);
            sourceRecords = 0;
            games = 0;
            wins = 0;
        }
    }

    static final class RuneOptionAccumulator {

        private final Map<String, int[]> values = new LinkedHashMap<>();
        private final Map<String, RuneSignature> configurations = new LinkedHashMap<>();
        private final Map<String, Map<RuneSignature, int[]>> completeShardVariants = new LinkedHashMap<>();

        void add(RuneSignature signature, boolean win) {
            String key = signature.toKey();
            ChampionBuildEngine.add(values, key, win);
            configurations.putIfAbsent(key, signature);
            if (signature.statShards().size() == 3)
                ChampionBuildEngine.add(completeShardVariants.computeIfAbsent(key, ignored -> new LinkedHashMap<>()),
                    signature, win);
        }

        List<Build.RuneOption> toOptions(int totalGames) {
            List<Map.Entry<String, int[]>> entries = sortedByGames(values, String::compareTo);

            List<Build.RuneOption> result = new ArrayList<>();
            for (Map.Entry<String, int[]> entry : entries) {
                int matches = entry.getValue()[0];
                int wins = entry.getValue()[1];
                result.add(new Build.RuneOption(entry.getKey(), bestSignature(entry.getKey()), matches, wins,
                    rate(wins, matches), rate(matches, totalGames)));
            }
            return result;
        }

        private RuneSignature bestSignature(String key) {
            RuneSignature best = configurations.get(key);
            int[] bestStats = null;
            Map<RuneSignature, int[]> variants = completeShardVariants.get(key);
            if (variants == null) return best;
            for (Map.Entry<RuneSignature, int[]> entry : variants.entrySet()) {
                if (bestStats == null || isBetter(entry, best, bestStats)) {
                    best = entry.getKey();
                    bestStats = entry.getValue();
                }
            }
            return best;
        }

        private static boolean isBetter(Map.Entry<RuneSignature, int[]> candidate, RuneSignature current,
                                        int[] currentStats) {
            int[] candidateStats = candidate.getValue();
            int winrate = Double.compare(rate(candidateStats[1], candidateStats[0]),
                rate(currentStats[1], currentStats[0]));
            if (winrate != 0) return winrate > 0;
            if (candidateStats[0] != currentStats[0]) return candidateStats[0] > currentStats[0];
            return candidate.getKey().statShardsKey().compareTo(current.statShardsKey()) < 0;
        }

        void clear() {
            MatchMemoryUtils.release(values);
            MatchMemoryUtils.release(configurations);
            MatchMemoryUtils.release(completeShardVariants);
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

    private static void addCoreTimes(ChampionBuildData.Game game, Map<String, TimingStats> buildTimes,
                                     Map<Integer, TimingStats> itemTimes) {
        List<Integer> core = game.signature().core();
        if (core.isEmpty()) return;
        long completedAt = 0;
        boolean complete = true;
        for (Integer item : core) {
            Long acquiredAt = game.itemPurchaseTimesMillis().get(item);
            if (acquiredAt == null) {
                complete = false;
                continue;
            }
            addTime(itemTimes, item, acquiredAt);
            completedAt = Math.max(completedAt, acquiredAt);
        }
        if (complete) addTime(buildTimes, BuildUtils.joinInts(core), completedAt);
    }

    private static void addStarter(ChampionBuildData.Game game, Map<String, int[]> values) {
        add(values, BuildUtils.joinInts(game.signature().starter()), game.win());
    }

    private static void addTimedItem(ChampionBuildData.Game game, int item, Map<Integer, int[]> values,
                                     Map<Integer, TimingStats> times) {
        if (item == 0) return;
        add(values, item, game.win());
        addItemTime(game, item, times);
    }

    private static void addSlots(ChampionBuildData.Game game, Map<Integer, Map<Integer, int[]>> values,
                                 Map<Integer, Map<Integer, TimingStats>> times) {
        BuildSignature signature = game.signature();
        Set<Integer> excluded = coreExcluded(signature);
        List<Integer> extra = new ArrayList<>();
        for (Integer item : signature.fullBuild())
            if (!excluded.contains(item) && !signature.starter().contains(item)) extra.add(item);
        for (int slot = 0; slot < SLOT_COUNT && slot < extra.size(); slot++) {
            add(values, slot, extra.get(slot), game.win());
            Long acquiredAt = game.itemPurchaseTimesMillis().get(extra.get(slot));
            if (acquiredAt != null) addTime(times.computeIfAbsent(slot, ignored -> new LinkedHashMap<>()),
                extra.get(slot), acquiredAt);
        }
    }

    private static void addItemTime(ChampionBuildData.Game game, int item,
                                    Map<Integer, TimingStats> times) {
        Long acquiredAt = game.itemPurchaseTimesMillis().get(item);
        if (acquiredAt != null) addTime(times, item, acquiredAt);
    }

    private static <K> void addTime(Map<K, TimingStats> values, K key, long timestampMillis) {
        values.computeIfAbsent(key, ignored -> new TimingStats()).add(timestampMillis / 1000.0);
    }

    private static void addRunes(ChampionBuildData.Game game, RuneOptionAccumulator values) {
        RuneSignature configuration = game.runes();
        if (configuration == null) return;
        values.add(configuration, game.win());
    }

    private static void addSummonerSpells(ChampionBuildData.Game game, Map<String, int[]> values) {
        if (!game.signature().summonerSpells().isEmpty())
            add(values, BuildUtils.joinInts(game.signature().summonerSpells()), game.win());
    }

    private static void addSkillOrder(ChampionBuildData.Game game, SkillOrderTrie values) {
        values.add(game.signature().spellOrder(), game.win(), game.skillUpgrades());
    }

    private static void addPrismatics(ChampionBuildData.Game game, Map<Integer, int[]> values,
                                      Map<Integer, TimingStats> times) {
        for (Integer item : game.signature().prismatics()) {
            add(values, item, game.win());
            addItemTime(game, item, times);
        }
    }

    private static void addAugments(ChampionBuildData.Game game, Map<Integer, Map<Integer, int[]>> values) {
        List<Integer> augmentIds = game.signature().augments();
        for (int slot = 0; slot < AUGMENT_SLOT_COUNT && slot < augmentIds.size(); slot++)
            add(values, slot, augmentIds.get(slot), game.win());
    }

    private static List<Build.CoreBuildOption> toCoreBuilds(Map<String, int[]> values,
                                                              Map<String, List<Integer>> items,
                                                              Map<String, TimingStats> times, int totalGames) {
        List<Build.CoreBuildOption> result = new ArrayList<>();
        List<Map.Entry<String, int[]>> entries = sortedByGames(values, String::compareTo);
        for (Map.Entry<String, int[]> entry : entries) {
            int matches = entry.getValue()[0];
            int wins = entry.getValue()[1];
            result.add(new Build.CoreBuildOption(entry.getKey(),
                items.getOrDefault(entry.getKey(), parseIds(entry.getKey())), matches, wins,
                rate(wins, matches), rate(matches, totalGames), average(times.get(entry.getKey())),
                timedMatches(times.get(entry.getKey()))));
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

    private static List<Build.Option> toTimedOptions(Map<Integer, int[]> values,
                                                      Map<Integer, TimingStats> times, int totalGames) {
        List<Build.Option> result = new ArrayList<>();
        List<Map.Entry<Integer, int[]>> entries = sortedByGames(values, Integer::compareTo);
        for (Map.Entry<Integer, int[]> entry : entries) {
            TimingStats timing = times.get(entry.getKey());
            int matches = entry.getValue()[0];
            int wins = entry.getValue()[1];
            result.add(new Build.Option(String.valueOf(entry.getKey()), matches, wins, rate(wins, matches),
                rate(matches, totalGames), average(timing), timedMatches(timing)));
        }
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
                                                     Map<Integer, Map<Integer, TimingStats>> times,
                                                     int count, int totalGames) {
        List<List<Build.Option>> result = new ArrayList<>();
        for (int slot = 0; slot < count; slot++)
            result.add(toTimedOptions(values.getOrDefault(slot, Map.of()),
                times.getOrDefault(slot, Map.of()), totalGames));
        return result;
    }

    private static List<List<Build.Option>> toSlots(Map<Integer, Map<Integer, int[]>> values,
                                                     int count, int totalGames) {
        List<List<Build.Option>> result = new ArrayList<>();
        for (int slot = 0; slot < count; slot++)
            result.add(toOptions(values.getOrDefault(slot, Map.of()), totalGames));
        return result;
    }

    private static Build.Option option(String id, int[] stats, int totalGames) {
        int matches = stats[0];
        int wins = stats[1];
        return new Build.Option(id, matches, wins, rate(wins, matches), rate(matches, totalGames));
    }

    private static Double average(TimingStats timing) {
        return timing == null || timing.count == 0 ? null : timing.sum / timing.count;
    }

    private static int timedMatches(TimingStats timing) {
        return timing == null ? 0 : timing.count;
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

    private static <K> void add(Map<K, int[]> values, K key, boolean win) {
        if (key == null || key instanceof String string && string.isBlank()) return;
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

    private static final class TimingStats {
        private double sum;
        private int count;

        private void add(double value) {
            sum += value;
            count++;
        }
    }

    static final class SkillOrderTrie {

        private final SkillOrderNode root = new SkillOrderNode();

        void add(List<Integer> order, boolean win) {
            add(order, win, List.of());
        }

        void add(List<Integer> order, boolean win, List<ChampionBuildTimelineUtils.SkillUpgrade> upgrades) {
            if (order == null || order.isEmpty()) return;

            SkillOrderNode current = root;
            int depth = 0;
            for (Integer ability : order) {
                if (ability == null || ability < 1 || ability > MAX_ABILITY_SLOT) break;
                current = current.children.computeIfAbsent(ability, ignored -> new SkillOrderNode());
                depth++;
                if (upgrades != null && depth <= upgrades.size()) {
                    ChampionBuildTimelineUtils.SkillUpgrade upgrade = upgrades.get(depth - 1);
                    if (upgrade.skillSlot() == ability && upgrade.championLevel() != null)
                        current.times.computeIfAbsent(upgrade.championLevel(), ignored -> new TimingStats())
                            .add(upgrade.timestampMillis() / 1000.0);
                }
            }
            if (depth == 0) return;

            current.matches++;
            if (win) current.wins++;
        }

        List<Build.SkillOrderOption> toOptions(int totalGames) {
            int targetDepth = deepestObservedDepth(root, 0);
            if (targetDepth == 0) return List.of();

            List<SkillOrderCandidate> candidates = new ArrayList<>();
            collectCandidates(root, new ArrayList<>(), new ArrayList<>(), 0, 0, targetDepth, candidates);
            candidates.sort((left, right) -> {
                int support = Integer.compare(right.matches(), left.matches());
                if (support != 0) return support;
                int exact = Integer.compare(right.exactMatches(), left.exactMatches());
                return exact != 0 ? exact : left.id().compareTo(right.id());
            });

            List<Build.SkillOrderOption> result = new ArrayList<>();
            for (SkillOrderCandidate candidate : candidates)
                result.add(new Build.SkillOrderOption(candidate.id(), candidate.order(), candidate.matches(),
                    candidate.wins(), rate(candidate.wins(), candidate.matches()), rate(candidate.matches(), totalGames),
                    candidate.timedOrder()));
            return result;
        }

        void clear() {
            root.clear();
        }

        private static int deepestObservedDepth(SkillOrderNode node, int depth) {
            int result = node.matches > 0 ? depth : 0;
            for (SkillOrderNode child : node.children.values())
                result = Math.max(result, deepestObservedDepth(child, depth + 1));
            return result;
        }

        private static void collectCandidates(SkillOrderNode node, List<Integer> order,
                                              List<SkillOrderNode> path, int prefixMatches,
                                              int prefixWins, int targetDepth,
                                              List<SkillOrderCandidate> candidates) {
            int matches = prefixMatches + node.matches;
            int wins = prefixWins + node.wins;
            if (order.size() == targetDepth) {
                if (node.matches > 0) candidates.add(new SkillOrderCandidate(BuildUtils.joinInts(order),
                    List.copyOf(order), matches, wins, node.matches, skillTimings(order, path)));
                return;
            }

            for (Map.Entry<Integer, SkillOrderNode> entry : node.children.entrySet()) {
                order.add(entry.getKey());
                path.add(entry.getValue());
                collectCandidates(entry.getValue(), order, path, matches, wins, targetDepth, candidates);
                path.remove(path.size() - 1);
                order.remove(order.size() - 1);
            }
        }

        private static List<Build.SkillTiming> skillTimings(List<Integer> order, List<SkillOrderNode> path) {
            List<Build.SkillTiming> result = new ArrayList<>();
            for (int index = 0; index < path.size(); index++) {
                SkillOrderNode node = path.get(index);
                for (Map.Entry<Integer, TimingStats> timing : node.times.entrySet()) {
                    TimingStats stats = timing.getValue();
                    result.add(new Build.SkillTiming(timing.getKey(), order.get(index),
                        stats.sum / stats.count, stats.count));
                }
            }
            result.sort(Comparator.comparing(Build.SkillTiming::level, Comparator.nullsLast(Integer::compareTo))
                .thenComparingInt(Build.SkillTiming::abilitySlot));
            return List.copyOf(result);
        }
    }

    private static final class SkillOrderNode {

        private final Map<Integer, SkillOrderNode> children = new LinkedHashMap<>();
        private final Map<Integer, TimingStats> times = new LinkedHashMap<>();
        private int matches;
        private int wins;

        private void clear() {
            for (SkillOrderNode child : children.values()) child.clear();
            children.clear();
            times.clear();
            matches = 0;
            wins = 0;
        }
    }

    private record SkillOrderCandidate(String id, List<Integer> order, int matches, int wins, int exactMatches,
                                       List<Build.SkillTiming> timedOrder) {}
}
