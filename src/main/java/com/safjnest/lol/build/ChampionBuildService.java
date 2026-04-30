package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.build.ChampionBuild.SlotOption;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import java.util.*;
import java.util.function.Function;

public class ChampionBuildService {

    private static final int MIN_GAMES    = 5;
    private static final int SLOT_OPTIONS = 3;
    private static final int AUGMENT_OPTIONS = 10;
    private static final int PRISMATIC_OPTIONS = 10;
    private static final int SUMMONER_SPELLS_OPTIONS = 3;

    private record StatsResult(
            Map<String, int[]> stats,
            Map<String, String> representativeByGroup,
            Map<String, Map<Integer, Integer>> itemFreqByGroup,
            Map<String, Map<String, Integer>> variantsByGroup,
            Map<String, Map<String, Integer>> spellOrdersByGroup,
            Map<String, Map<Integer, Map<Integer, int[]>>> slotStatsByGroup,
            Map<String, Map<Integer, int[]>> bootsStatsByGroup,
            Map<String, Map<Integer, int[]>> suppItemStatsByGroup,
            Map<String, Map<Integer, int[]>> prismaticsStatsByGroup,
            Map<Integer, int[]> augmentsStatsByGroup,
            Map<String, int[]> summonerSpellsStatsByGroup
    ) {}

    // -------------------------------------------------------------------------

    public List<ChampionBuild> getAll(ChampionFilter filter) {
        ChampionBuild cached = LeagueDB.getChampionBuild(filter);
        if (cached != null) return Collections.singletonList(cached);

        List<ChampionBuild> computed = computeAll(filter);
        if (computed != null && !computed.isEmpty()) {
            computed.forEach(LeagueDB::saveChampionBuild);
        }
        return computed;
    }

    // -------------------------------------------------------------------------

    private List<ChampionBuild> computeAll(ChampionFilter filter) {
        QueryResult result = LeagueDB.getChampionBuildsRaw(filter);
        StatsResult buildSr = computeBuildStats(filter, result);
        StatsResult runeSr  = computeRuneStats(result);

        RuneSignature topRunes = top(runeSr.stats(), RuneSignature::decode, false);

        return buildSr.stats().entrySet().stream()
                .filter(e -> e.getValue()[0] >= MIN_GAMES)
                .map(e -> buildFromGroup(e.getKey(), e.getValue(), buildSr, topRunes, filter))
                .filter(Objects::nonNull)
                .toList();
    }

    private ChampionBuild buildFromGroup(String groupKey, int[] groupStats, StatsResult buildSr,
                                          RuneSignature runes, ChampionFilter filter) {
        String repKey = buildSr.representativeByGroup().get(groupKey);
        if (repKey == null) return null;

        BuildSignature base = BuildSignature.decode(repKey);
        List<Integer> spellOrder = mergeSpellOrder(buildSr.spellOrdersByGroup().getOrDefault(groupKey, Collections.emptyMap()));

        Map<Integer, Map<Integer, int[]>> slotStats = buildSr.slotStatsByGroup().getOrDefault(groupKey, Collections.emptyMap());

        List<List<ChampionBuild.SlotOption>> slots = new ArrayList<>();
        for (int slot = 4; slot < 8; slot++) {
            Map<Integer, int[]> itemStats = slotStats.getOrDefault(slot, Collections.emptyMap());
            List<ChampionBuild.SlotOption> options = itemStats.entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> -e.getValue()[0]))
                    .limit(SLOT_OPTIONS)
                    .map(e -> new ChampionBuild.SlotOption(
                            e.getKey(), e.getValue()[0],
                            e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
                    .toList();
            slots.add(options);
        }

        List<SlotOption> boots = buildSr.bootsStatsByGroup().getOrDefault(groupKey, Collections.emptyMap()).entrySet().stream()
            .map(e -> new SlotOption(e.getKey(), e.getValue()[0], e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
            .sorted(Comparator.comparingInt(SlotOption::matches).reversed())
            .limit(SLOT_OPTIONS)
            .toList();

        List<SlotOption> suppItem = buildSr.suppItemStatsByGroup().getOrDefault(groupKey, Collections.emptyMap()).entrySet().stream()
            .map(e -> new SlotOption(e.getKey(), e.getValue()[0], e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
            .sorted(Comparator.comparingInt(SlotOption::matches).reversed())
            .limit(SLOT_OPTIONS)
            .toList();

        List<SlotOption> prismaticOpts = buildSr.prismaticsStatsByGroup().getOrDefault(groupKey, Collections.emptyMap()).entrySet().stream()
                .map(e -> new SlotOption(e.getKey(), e.getValue()[0],
                        e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
                .sorted(Comparator.comparingInt(SlotOption::matches).reversed())
                .limit(PRISMATIC_OPTIONS)
                .toList();

        List<List<SlotOption>> prismatics = prismaticOpts.isEmpty() ? List.of() : List.of(prismaticOpts);

        List<SlotOption> augments = buildSr.augmentsStatsByGroup().entrySet().stream()
            .map(e -> new SlotOption(e.getKey(), e.getValue()[0], e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
            .sorted(Comparator.comparingInt(SlotOption::matches).reversed())
            .limit(AUGMENT_OPTIONS)
            .toList();

        List<SlotOption> summonerSpells = buildSr.summonerSpellsStatsByGroup().entrySet().stream()
            .map(e -> {
                String[] parts = e.getKey().split("-");
                int key = Integer.parseInt(parts[0]) * 100 + Integer.parseInt(parts[1]);
                return new SlotOption(key, e.getValue()[0], e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0);
            })
            .sorted(Comparator.comparingInt(SlotOption::matches).reversed())
            .limit(SUMMONER_SPELLS_OPTIONS)
            .toList();

        return new ChampionBuild(filter, base.starter(), boots, suppItem,
                base.core(), slots, prismatics, summonerSpells, augments, spellOrder, runes, groupStats[0],
                groupStats[0] > 0 ? (double) groupStats[1] / groupStats[0] : 0);
    }

    // -------------------------------------------------------------------------

    private StatsResult computeBuildStats(ChampionFilter filter, QueryResult result) {
        Map<String, int[]> stats                             = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> variantsByGroup    = new HashMap<>();
        Map<String, Map<Integer, Integer>> itemFreqByGroup   = new HashMap<>();
        Map<String, Map<String, Integer>> spellOrdersByGroup = new HashMap<>();
        Map<String, Map<Integer, Map<Integer, int[]>>> slotStatsByGroup = new HashMap<>();
        Map<String, Map<Integer, int[]>> bootsStatsByGroup = new HashMap<>();
        Map<String, Map<Integer, int[]>> suppItemStatsByGroup = new HashMap<>();
        Map<String, Map<Integer, int[]>> prismaticsStatsByGroup = new HashMap<>();
        Map<Integer, int[]> augmentsStatsByGroup = new HashMap<>();
        Map<String, int[]> summonerSpellsStatsByGroup = new HashMap<>();

        for (QueryRecord record : result) {
            boolean win    = record.getAsBoolean("win");
            String rawJson = record.get("build");
            if (rawJson == null) continue;

            JSONObject full;
            try { full = new JSONObject(rawJson); } catch (Exception ex) { continue; }

            JSONObject buildObj  = full.optJSONObject("build");
            JSONArray skillOrder = full.optJSONArray("skill_order");
            JSONArray prismatics = full.optJSONArray("prismatics");
            JSONArray augments = full.optJSONArray("augments");
            JSONArray summonerSpells = full.optJSONArray("summoner_spells");
            if (buildObj == null || skillOrder == null || buildObj.optJSONArray("build") == null) continue;

            BuildSignature sig = BuildSignature.from(full, skillOrder, prismatics, augments, summonerSpells, filter);
            System.out.println("--------------------------------");
            System.out.println("sig: " + sig);
            System.out.println("--------------------------------");
            if (sig == null) continue;

            String coreKey = sig.toCoreKey();
            

            System.out.println("--------------------------------");
            System.out.println("coreKey: " + coreKey);
            System.out.println("sig.toKey(): " + sig.toKey());
            System.out.println("--------------------------------");

            variantsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>()).merge(sig.toKey(), 1, Integer::sum);
            itemFreqByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
            sig.fullBuild().forEach(id -> itemFreqByGroup.get(coreKey).merge(id, 1, Integer::sum));
            spellOrdersByGroup.computeIfAbsent(coreKey, k -> new HashMap<>())
                    .merge(BuildUtils.joinInts(sig.spellOrder()), 1, Integer::sum);

            if (sig.prismatics() != null && !sig.prismatics().isEmpty()) {
                Map<Integer, int[]> prismaticsStats = prismaticsStatsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
                for (int prismaticId : sig.prismatics()) {
                    int[] row = prismaticsStats.computeIfAbsent(prismaticId, k -> new int[2]);
                    row[0]++;
                    if (win) row[1]++;
                }
            }


            Map<Integer, int[]> bootsStats = bootsStatsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
            int[] b = bootsStats.computeIfAbsent(sig.boots(), k -> new int[2]);
            b[0]++;
            if (win) b[1]++;

            Map<Integer, int[]> suppItemStats = suppItemStatsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
            int[] s = suppItemStats.computeIfAbsent(sig.suppItem(), k -> new int[2]);
            s[0]++;
            if (win) s[1]++;

            List<Integer> extra = sig.fullBuild().stream()
                    .filter(id -> !coreExcluded(sig).contains(id) && !sig.starter().contains(id))
                    .toList();

            Map<Integer, Map<Integer, int[]>> slotStats = slotStatsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
            if (extra.size() >= 1) addSlot(slotStats, 4, extra.get(0), win);
            if (extra.size() >= 2) addSlot(slotStats, 5, extra.get(1), win);
            if (extra.size() >= 3) addSlot(slotStats, 6, extra.get(2), win);
            if (extra.size() >= 4) addSlot(slotStats, 6, extra.get(3), win);
            if (extra.size() >= 5) addSlot(slotStats, 7, extra.get(4), win);

            if (sig.augments() != null && !sig.augments().isEmpty()) {
                for (int augmentId : sig.augments()) {
                    int[] row = augmentsStatsByGroup.computeIfAbsent(augmentId, k -> new int[2]);
                    row[0]++;
                    if (win) row[1]++;
                    
                }
            }

            stats.computeIfAbsent(coreKey, k -> new int[2]);
            stats.get(coreKey)[0]++;
            if (win) stats.get(coreKey)[1]++;

            if (sig.summonerSpells() != null && !sig.summonerSpells().isEmpty()) {
                String key = BuildUtils.joinInts(sig.summonerSpells());
                summonerSpellsStatsByGroup.computeIfAbsent(key, k -> new int[2]);
                summonerSpellsStatsByGroup.get(key)[0]++;
                if (win) summonerSpellsStatsByGroup.get(key)[1]++;
            }

            System.out.println("--------------------------------");
            System.out.println("game_id: " + record.get("game_id"));
            System.out.println("core: " + BuildUtils.toItemName(sig.starter()));
            System.out.println("--------------------------------");
        }

        return new StatsResult(stats, resolveRepresentatives(variantsByGroup), itemFreqByGroup,
                variantsByGroup, spellOrdersByGroup, slotStatsByGroup, bootsStatsByGroup, suppItemStatsByGroup,
                prismaticsStatsByGroup, augmentsStatsByGroup, summonerSpellsStatsByGroup);
    }

    private void addSlot(Map<Integer, Map<Integer, int[]>> slotStats, int slot, int id, boolean win) {
        int[] s = slotStats.computeIfAbsent(slot, k -> new HashMap<>()).computeIfAbsent(id, k -> new int[2]);
        s[0]++;
        if (win) s[1]++;
    }

    private StatsResult computeRuneStats(QueryResult result) {
        Map<String, int[]> stats = new LinkedHashMap<>();

        for (QueryRecord record : result) {
            boolean win    = record.getAsBoolean("win");
            String rawJson = record.get("build");
            if (rawJson == null) continue;

            JSONObject full;
            try { full = new JSONObject(rawJson); } catch (Exception ex) { continue; }

            JSONObject runesObj = full.optJSONObject("runes");
            if (runesObj == null) continue;

            RuneSignature sig = RuneSignature.from(runesObj);
            if (sig == null) continue;

            String key = sig.toKey();
            stats.computeIfAbsent(key, k -> new int[2]);
            stats.get(key)[0]++;
            if (win) stats.get(key)[1]++;
        }

        return new StatsResult(stats, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    // -------------------------------------------------------------------------

    private Map<String, String> resolveRepresentatives(Map<String, Map<String, Integer>> variantsByGroup) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : variantsByGroup.entrySet()) {
            entry.getValue().entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> itemCountFromKey(e.getKey()))
                            .thenComparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .ifPresent(rep -> result.put(entry.getKey(), rep));
        }
        return result;
    }

    private List<Integer> mergeSpellOrder(Map<String, Integer> variants) {
        int[][] votes = new int[18][5];
        for (Map.Entry<String, Integer> e : variants.entrySet()) {
            String key = e.getKey();
            int count = e.getValue();
            List<Integer> steps = key.contains("-")
                    ? BuildUtils.parseDashList(key)
                    : spellOrderFromLegacyString(key);
            for (int i = 0; i < 18 && i < steps.size(); i++) {
                int s = steps.get(i);
                if (s >= 1 && s <= 4) votes[i][s] += count;
            }
        }
        List<Integer> out = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) {
            int best = 0, bestV = 0;
            for (int s = 1; s <= 4; s++) if (votes[i][s] > bestV) { bestV = votes[i][s]; best = s; }
            out.add(best == 0 ? 0 : best);
        }
        return out;
    }

    private static List<Integer> spellOrderFromLegacyString(String key) {
        List<Integer> o = new ArrayList<>(18);
        for (int i = 0; i < key.length() && i < 18; i++)
            o.add(Character.getNumericValue(key.charAt(i)));
        while (o.size() < 18) o.add(0);
        return o;
    }

    private Set<Integer> coreExcluded(BuildSignature base) {
        Set<Integer> ex = new HashSet<>(base.core());
        ex.add(base.boots());
        if (base.suppItem() != 0) ex.add(base.suppItem());
        ex.addAll(base.prismatics());
        ex.addAll(base.augments());
        return ex;
    }

    private String topKey(Map<String, int[]> stats, boolean byWinrate) {
        return stats.entrySet().stream()
                .filter(e -> !byWinrate || e.getValue()[0] >= MIN_GAMES)
                .max(byWinrate
                        ? Comparator.comparingDouble(e -> (double) e.getValue()[1] / e.getValue()[0])
                        : Comparator.comparingInt(e -> e.getValue()[0]))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private <T> T top(Map<String, int[]> stats, Function<String, T> decoder, boolean byWinrate) {
        String key = topKey(stats, byWinrate);
        return key != null ? decoder.apply(key) : null;
    }

    private int itemCountFromKey(String key) {
        try { return BuildSignature.decode(key).fullBuild().size(); } catch (Exception e) { return 0; }
    }
}