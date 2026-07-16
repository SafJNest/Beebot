package com.safjnest.lol.service;

import com.safjnest.lol.build.BuildSignature;
import com.safjnest.lol.build.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildService {

    private static final int OPTION_LIMIT = 3;
    private static final int SLOT_COUNT = 4;
    private static final int AUGMENT_SLOT_COUNT = 4;

    private record AggregateStats(
        int games,
        int wins,
        Map<String, int[]> coreBuilds,
        Map<String, List<Integer>> coreBuildItems,
        Map<Integer, int[]> coreItems,
        Map<String, int[]> starters,
        Map<Integer, int[]> boots,
        Map<Integer, int[]> supportItems,
        Map<Integer, Map<Integer, int[]>> slots,
        Map<String, int[]> runes,
        Map<String, int[]> summonerSpells,
        Map<String, int[]> skillOrders,
        Map<Integer, int[]> prismatics,
        Map<Integer, Map<Integer, int[]>> augments
    ) {}

    public List<Build> getAll(Filter filter) {
        return loadBuilds(filter, true);
    }

    public Build getAggregate(Filter filter) {
        return getAggregate(filter, true);
    }

    public List<Build> recomputeAll(Filter filter) {
        List<Build> computed = computeAll(filter);
        if (computed != null && !computed.isEmpty()) LeagueDB.saveChampionBuilds(computed);
        return computed;
    }

    // ============================================================================

    Build getAggregate(Filter filter, boolean allowCompute) {
        if (filter == null) return null;
        List<Build> builds = loadBuilds(filter, allowCompute);
        return builds.isEmpty() ? null : builds.get(0);
    }

    private List<Build> loadBuilds(Filter filter, boolean allowCompute) {
        if (filter == null) return List.of();

        List<Build> stored;
        try {
            stored = LeagueDB.getChampionBuild(filter);
        } catch (RuntimeException exception) {
            if (!allowCompute) return List.of();
            throw exception;
        }
        if (stored != null && !stored.isEmpty()) return stored;
        if (!allowCompute) return List.of();

        List<Build> computed = computeAll(filter);
        if (computed != null && !computed.isEmpty()) computed.forEach(LeagueDB::saveChampionBuild);
        return computed == null ? List.of() : computed;
    }

    private List<Build> computeAll(Filter filter) {
        QueryResult result = LeagueDB.getChampionBuildsRaw(filter);
        System.out.println("result: " + result);
        AggregateStats stats = computeAggregate(result, filter);
        System.out.println("stats: " + stats);
        if (stats.games() == 0) return List.of();

        Build aggregate = new Build(
            filter,
            stats.games(),
            stats.wins(),
            rate(stats.wins(), stats.games()),
            toCoreBuilds(stats.coreBuilds(), stats.coreBuildItems(), stats.games()),
            toOptions(stats.coreItems(), stats.games()),
            toConfigOptions(stats.starters(), stats.games()),
            toOptions(stats.boots(), stats.games()),
            toOptions(stats.supportItems(), stats.games()),
            toSlots(stats.slots(), SLOT_COUNT, stats.games()),
            toRuneOptions(stats.runes(), stats.games()),
            toConfigOptions(stats.summonerSpells(), stats.games()),
            toSkillOrders(stats.skillOrders(), stats.games()),
            toOptions(stats.prismatics(), stats.games()),
            toSlots(stats.augments(), AUGMENT_SLOT_COUNT, stats.games())
        );
        return List.of(aggregate);
    }

    private AggregateStats computeAggregate(QueryResult result, Filter filter) {
        int games = 0;
        int wins = 0;
        Map<String, int[]> coreBuilds = new LinkedHashMap<>();
        Map<String, List<Integer>> coreBuildItems = new LinkedHashMap<>();
        Map<Integer, int[]> coreItems = new LinkedHashMap<>();
        Map<String, int[]> starters = new LinkedHashMap<>();
        Map<Integer, int[]> boots = new LinkedHashMap<>();
        Map<Integer, int[]> supportItems = new LinkedHashMap<>();
        Map<Integer, Map<Integer, int[]>> slots = new LinkedHashMap<>();
        Map<String, int[]> runes = new LinkedHashMap<>();
        Map<String, int[]> summonerSpells = new LinkedHashMap<>();
        Map<String, int[]> skillOrders = new LinkedHashMap<>();
        Map<Integer, int[]> prismatics = new LinkedHashMap<>();
        Map<Integer, Map<Integer, int[]>> augments = new LinkedHashMap<>();

        for (QueryRecord record : result) {
            JSONObject full = json(record.get("build"));
            if (full == null) continue;

            JSONObject buildObject = full.optJSONObject("build");
            JSONArray skillOrder = full.optJSONArray("skill_order");
            if (buildObject == null || buildObject.optJSONArray("build") == null || skillOrder == null) continue;

            BuildSignature signature = BuildSignature.from(
                full,
                skillOrder,
                full.optJSONArray("prismatics"),
                full.optJSONArray("augments"),
                full.optJSONArray("summoner_spells"),
                filter
            );
            if (signature == null) continue;

            boolean win = record.getAsBoolean("win");
            games++;
            if (win) wins++;

            String coreBuildKey = BuildUtils.joinInts(signature.core());
            add(coreBuilds, coreBuildKey, win);
            coreBuildItems.putIfAbsent(coreBuildKey, signature.core());
            for (Integer id : signature.core()) add(coreItems, id, win);
            add(starters, BuildUtils.joinInts(signature.starter()), win);
            if (signature.boots() != 0) add(boots, signature.boots(), win);
            if (signature.suppItem() != 0) add(supportItems, signature.suppItem(), win);

            List<Integer> extra = signature.fullBuild().stream()
                .filter(id -> !coreExcluded(signature).contains(id) && !signature.starter().contains(id))
                .toList();
            for (int i = 0; i < SLOT_COUNT && i < extra.size(); i++)
                add(slots, i, extra.get(i), win);

            for (Integer id : signature.prismatics()) add(prismatics, id, win);
            for (int i = 0; i < AUGMENT_SLOT_COUNT && i < signature.augments().size(); i++)
                add(augments, i, signature.augments().get(i), win);

            if (!signature.summonerSpells().isEmpty())
                add(summonerSpells, BuildUtils.joinInts(signature.summonerSpells()), win);
            if (!signature.spellOrder().isEmpty())
                add(skillOrders, BuildUtils.joinInts(signature.spellOrder()), win);

            JSONObject runesObject = full.optJSONObject("runes");
            RuneSignature runeSignature = runesObject == null ? null : RuneSignature.from(runesObject);
            if (runeSignature != null) add(runes, runeSignature.toKey(), win);
        }

        return new AggregateStats(games, wins, coreBuilds, coreBuildItems, coreItems, starters, boots, supportItems,
            slots, runes, summonerSpells, skillOrders, prismatics, augments);
    }

    private static JSONObject json(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return new JSONObject(raw); }
        catch (RuntimeException ignored) { return null; }
    }

    private static void add(Map<Integer, int[]> values, int key, boolean win) {
        values.computeIfAbsent(key, ignored -> new int[2])[0]++;
        if (win) values.get(key)[1]++;
    }

    private static void add(Map<String, int[]> values, String key, boolean win) {
        if (key == null || key.isBlank()) return;
        values.computeIfAbsent(key, ignored -> new int[2])[0]++;
        if (win) values.get(key)[1]++;
    }

    private static void add(Map<Integer, Map<Integer, int[]>> values, int slot, int key, boolean win) {
        values.computeIfAbsent(slot, ignored -> new LinkedHashMap<>());
        add(values.get(slot), key, win);
    }

    private static List<Build.CoreBuildOption> toCoreBuilds(Map<String, int[]> values,
                                                            Map<String, List<Integer>> items,
                                                            int totalGames) {
        List<Build.CoreBuildOption> result = new ArrayList<>();
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(OPTION_LIMIT)
            .forEach(entry -> {
                int matches = entry.getValue()[0];
                int wins = entry.getValue()[1];
                result.add(new Build.CoreBuildOption(entry.getKey(), items.getOrDefault(entry.getKey(), parseIds(entry.getKey())), matches, wins,
                    rate(wins, matches), rate(matches, totalGames)));
            });
        return result;
    }

    private static List<Build.Option> toOptions(Map<Integer, int[]> values, int totalGames) {
        return values.entrySet().stream()
            .sorted(Comparator.comparingInt(Map.Entry::getKey))
            .limit(OPTION_LIMIT)
            .map(entry -> option(String.valueOf(entry.getKey()), entry.getValue(), totalGames))
            .toList();
    }

    private static List<Build.Option> toConfigOptions(Map<String, int[]> values, int totalGames) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(OPTION_LIMIT)
            .map(entry -> option(entry.getKey(), entry.getValue(), totalGames))
            .toList();
    }

    private static List<List<Build.Option>> toSlots(Map<Integer, Map<Integer, int[]>> values,
                                                     int count, int totalGames) {
        List<List<Build.Option>> result = new ArrayList<>();
        for (int slot = 0; slot < count; slot++)
            result.add(toOptions(values.getOrDefault(slot, Map.of()), totalGames));
        return result;
    }

    private static List<Build.RuneOption> toRuneOptions(Map<String, int[]> values, int totalGames) {
        List<Build.RuneOption> result = new ArrayList<>();
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(OPTION_LIMIT)
            .forEach(entry -> {
                RuneSignature signature;
                try { signature = RuneSignature.decode(entry.getKey()); }
                catch (RuntimeException ignored) { return; }
                int matches = entry.getValue()[0];
                int wins = entry.getValue()[1];
                result.add(new Build.RuneOption(entry.getKey(), signature, matches, wins,
                    rate(wins, matches), rate(matches, totalGames)));
            });
        return result;
    }

    private static List<Build.SkillOrderOption> toSkillOrders(Map<String, int[]> values, int totalGames) {
        List<Build.SkillOrderOption> result = new ArrayList<>();
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(OPTION_LIMIT)
            .forEach(entry -> {
                int matches = entry.getValue()[0];
                int wins = entry.getValue()[1];
                result.add(new Build.SkillOrderOption(entry.getKey(), parseIds(entry.getKey()), matches, wins,
                    rate(wins, matches), rate(matches, totalGames)));
            });
        return result;
    }

    private static Build.Option option(String id, int[] stats, int totalGames) {
        int matches = stats[0];
        int wins = stats[1];
        return new Build.Option(id, matches, wins, rate(wins, matches), rate(matches, totalGames));
    }

    private static List<Integer> parseIds(String key) {
        if (key == null || key.isBlank()) return List.of();
        try { return BuildUtils.parseDashList(key); }
        catch (RuntimeException ignored) { return List.of(); }
    }

    private static double rate(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0;
    }

    private static java.util.Set<Integer> coreExcluded(BuildSignature signature) {
        java.util.Set<Integer> excluded = new java.util.HashSet<>(signature.core());
        excluded.add(signature.boots());
        excluded.add(signature.suppItem());
        excluded.addAll(signature.prismatics());
        excluded.addAll(signature.augments());
        return excluded;
    }
}
