package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChampionBuildService {

    private static final Logger log = LoggerFactory.getLogger(ChampionBuildService.class);

    private static final int MIN_GAMES       = 5;
    private static final int FULL_BUILD_SIZE = 6;
    private static final int SUGGESTIONS_MAX = 3;
    private static final int SLOT_OPTIONS    = 3;

    public enum Strategy { HIGHEST_WR, MOST_USED }

    public record FlatBuild(
            BuildFilter filter,
            Strategy strategy,
            BuildSignature build,
            RuneSignature runes,
            int games,
            double winrate
    ) {}

    // coreKey → slotIdx(0,1,2) → itemId → [games, wins]
    private record StatsResult(
            Map<String, int[]> stats,
            Map<String, String> representativeByGroup,
            Map<String, Map<Integer, Integer>> itemFreqByGroup,
            Map<String, Map<String, Integer>> variantsByGroup,
            Map<String, Map<String, Integer>> spellOrdersByGroup,
            Map<String, Map<Integer, Map<Integer, int[]>>> slotStatsByGroup
    ) {}

    // -------------------------------------------------------------------------

    public FlatBuild get(BuildFilter filter, Strategy strategy, Connection conn) throws Exception {
        FlatBuild cached = retrieve(filter, strategy, conn);
        if (cached != null) return cached;

        FlatBuild computed = analyzeFlatBuild(filter, strategy, conn);
        save(computed, conn);
        return computed;
    }

    public ChampionBuild getSlotBreakdown(BuildFilter filter, Strategy strategy, Connection conn) throws Exception {
        StatsResult buildSr = computeBuildStats(filter, conn);
        StatsResult runeSr  = computeRuneStats(filter, conn);

        String groupKey = topKey(buildSr.stats(), strategy == Strategy.HIGHEST_WR);
        if (groupKey == null) return null;

        BuildSignature base = BuildSignature.decode(buildSr.representativeByGroup().get(groupKey));
        String spellOrder   = mergeSpellOrder(buildSr.spellOrdersByGroup().getOrDefault(groupKey, Collections.emptyMap()));

        Map<Integer, Map<Integer, int[]>> slotStats = buildSr.slotStatsByGroup().getOrDefault(groupKey, Collections.emptyMap());

        List<List<ChampionBuild.SlotOption>> slots = new ArrayList<>();
        for (int slot = 4; slot < 7; slot++) {
            Map<Integer, int[]> itemStats = slotStats.getOrDefault(slot, Collections.emptyMap());
            List<ChampionBuild.SlotOption> options = itemStats.entrySet().stream()
                    .sorted(strategy == Strategy.HIGHEST_WR
                            ? Comparator.<Map.Entry<Integer, int[]>>comparingDouble(e -> e.getValue()[0] > 0 ? -(double) e.getValue()[1] / e.getValue()[0] : 0)
                            : Comparator.comparingInt(e -> -e.getValue()[0]))
                    .limit(SLOT_OPTIONS)
                    .map(e -> new ChampionBuild.SlotOption(
                            e.getKey(),
                            e.getValue()[0],
                            e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
                    .toList();
            slots.add(options);
        }

        RuneSignature runes = top(runeSr.stats(), RuneSignature::decode, strategy == Strategy.HIGHEST_WR);

        int totalGames = buildSr.stats().values().stream().mapToInt(v -> v[0]).sum();
        int totalWins  = buildSr.stats().values().stream().mapToInt(v -> v[1]).sum();

        return new ChampionBuild(filter, strategy, base.starterItems(), base.boots(), base.suppItem(),
                base.coreItems(), slots, spellOrder, runes, totalGames,
                totalGames > 0 ? (double) totalWins / totalGames : 0);
    }

    private FlatBuild retrieve(BuildFilter filter, Strategy strategy, Connection conn) {
        // TODO: SELECT FROM champion_builds WHERE filter_key=? AND strategy=?
        return null;
    }

    private void save(FlatBuild b, Connection conn) {
        // TODO: INSERT INTO champion_builds (filter_key, strategy, build_key, rune_key, games, winrate, updated_at)
    }

    // -------------------------------------------------------------------------

    private FlatBuild analyzeFlatBuild(BuildFilter filter, Strategy strategy, Connection conn) throws Exception {
        StatsResult buildSr = computeBuildStats(filter, conn);
        StatsResult runeSr  = computeRuneStats(filter, conn);

        log.info("[ChampionBuild] filter={} strategy={} buildKeys={} runeKeys={}",
                filter.toKey(), strategy, buildSr.stats().size(), runeSr.stats().size());

        String groupKey      = topKey(buildSr.stats(), strategy == Strategy.HIGHEST_WR);
        BuildSignature build = resolveFlatBuild(groupKey, buildSr);
        RuneSignature runes  = top(runeSr.stats(), RuneSignature::decode, strategy == Strategy.HIGHEST_WR);

        int totalGames = buildSr.stats().values().stream().mapToInt(v -> v[0]).sum();
        int totalWins  = buildSr.stats().values().stream().mapToInt(v -> v[1]).sum();

        return new FlatBuild(filter, strategy, build, runes, totalGames,
                totalGames > 0 ? (double) totalWins / totalGames : 0);
    }

    private BuildSignature resolveFlatBuild(String groupKey, StatsResult sr) {
        if (groupKey == null) return null;
        String repKey = sr.representativeByGroup().get(groupKey);
        if (repKey == null) return null;

        BuildSignature base = BuildSignature.decode(repKey);

        Map<Integer, Integer> groupFreq  = sr.itemFreqByGroup().getOrDefault(groupKey, Collections.emptyMap());
        Map<Integer, Integer> globalFreq = sr.itemFreqByGroup().values().stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

        String spellOrder = mergeSpellOrder(sr.spellOrdersByGroup().getOrDefault(groupKey, Collections.emptyMap()));
        return enrichFlatBuild(base, groupFreq, globalFreq, spellOrder);
    }

    private BuildSignature enrichFlatBuild(BuildSignature base,
                                            Map<Integer, Integer> groupFreq,
                                            Map<Integer, Integer> globalFreq,
                                            String spellOrder) {
        List<Integer> full    = new ArrayList<>(base.fullBuildItems());
        Set<Integer> present  = new HashSet<>(full);

        fill(full, present, groupFreq,  FULL_BUILD_SIZE);
        fill(full, present, globalFreq, FULL_BUILD_SIZE);

        List<Integer> suggestions = new ArrayList<>();
        Set<Integer> suggExcluded = new HashSet<>(coreExcluded(base));
        suggExcluded.addAll(present);

        fill(suggestions, suggExcluded, groupFreq,  SUGGESTIONS_MAX);
        fill(suggestions, suggExcluded, globalFreq, SUGGESTIONS_MAX);

        full        = full.subList(0, Math.min(full.size(), FULL_BUILD_SIZE));
        suggestions = new ArrayList<>(suggestions.subList(0, Math.min(suggestions.size(), SUGGESTIONS_MAX)));
        suggestions.removeAll(new HashSet<>(full));

        return new BuildSignature(
                base.starter(), base.boots(), base.suppItem(), base.core(),
                RuneSignature.joinInts(full),
                RuneSignature.joinInts(suggestions),
                spellOrder
        );
    }

    // -------------------------------------------------------------------------

    private StatsResult computeBuildStats(BuildFilter filter, Connection conn) throws Exception {
        Map<String, int[]> stats                             = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> variantsByGroup    = new HashMap<>();
        Map<String, Map<Integer, Integer>> itemFreqByGroup   = new HashMap<>();
        Map<String, Map<String, Integer>> spellOrdersByGroup = new HashMap<>();
        // coreKey → slotIdx → itemId → [games, wins]
        Map<String, Map<Integer, Map<Integer, int[]>>> slotStatsByGroup = new HashMap<>();

        String sql = "SELECT m.game_id, p.win, p.build, p.summoner_id FROM participant p JOIN `match` m ON m.id = p.match_id " + filter.sql();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                boolean win    = rs.getInt("win") == 1;
                String rawJson = rs.getString("build");
                if (rawJson == null) continue;

                JSONObject full;
                try { full = new JSONObject(rawJson); } catch (Exception ex) { continue; }

                JSONObject buildObj  = full.optJSONObject("build");
                JSONArray skillOrder = full.optJSONArray("skill_order");
                if (buildObj == null || skillOrder == null || buildObj.optJSONArray("build") == null) continue;

                String gameId = rs.getString("game_id");
                String summonerId = rs.getString("summoner_id");
                BuildSignature sig = BuildSignature.from(full, skillOrder);
                if (sig == null) continue;
                String coreKey = sig.toCoreKey();

                // variants + itemFreq
                variantsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>()).merge(sig.toKey(), 1, Integer::sum);
                itemFreqByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
                sig.fullBuildItems().forEach(id -> itemFreqByGroup.get(coreKey).merge(id, 1, Integer::sum));
                spellOrdersByGroup.computeIfAbsent(coreKey, k -> new HashMap<>()).merge(sig.spellOrder(), 1, Integer::sum);

                // slot stats: items beyond core+boots+supp, per position
                Set<Integer> excluded = coreExcluded(sig);
                List<Integer> extra = sig.fullBuildItems().stream()
                .filter(id -> !coreExcluded(sig).contains(id))
                .toList();
                Map<Integer, Map<Integer, int[]>> slotStats = slotStatsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
                // System.out.println("keystone=" + sig.toCoreKey());
                // System.out.println("core=" + ChampionBuild.a(sig.coreItems()));
                // System.out.println("extra=" + ChampionBuild.a(extra));
                System.out.println("--------------------------------");
                System.out.println("gameId=" + gameId);
                System.out.println("summonerId=" + summonerId);
                System.out.println("fullbuild=" + ChampionBuild.a(sig.fullBuildItems()));
                System.out.println("--------------------------------");

                for (int pos = 0; pos < extra.size(); pos++) {
                    int slot = 4 + pos; // slot4=4, slot5=5, slot6=6
                    if (slot > 6) break; // non vogliamo slot oltre il 6

                    int id = extra.get(pos);
                    System.out.println("slot=" + slot + " id=" + id);

                    // mappa per questo slot
                    Map<Integer, int[]> slotMap = slotStats.computeIfAbsent(slot, k -> new HashMap<>());

                    // prendi stat esistente o inizializza
                    int[] stat = slotMap.computeIfAbsent(id, k -> new int[2]);
                    stat[0]++;         // match count
                    if (win) stat[1]++; // win count
                }

                stats.computeIfAbsent(coreKey, k -> new int[2]);
                stats.get(coreKey)[0]++;
                if (win) stats.get(coreKey)[1]++;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        return new StatsResult(stats, resolveRepresentatives(variantsByGroup), itemFreqByGroup,
                variantsByGroup, spellOrdersByGroup, slotStatsByGroup);
    }

    private StatsResult computeRuneStats(BuildFilter filter, Connection conn) throws Exception {
        Map<String, int[]> stats = new LinkedHashMap<>();

        String sql = "SELECT p.win, p.build FROM participant p JOIN `match` m ON m.id = p.match_id " + filter.sql();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                boolean win    = rs.getInt("win") == 1;
                String rawJson = rs.getString("build");
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
        }

        return new StatsResult(stats, Collections.emptyMap(), Collections.emptyMap(),
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

    private String mergeSpellOrder(Map<String, Integer> variants) {
        int[][] votes = new int[18][5];
        for (Map.Entry<String, Integer> e : variants.entrySet()) {
            String spell = e.getKey(); int count = e.getValue();
            for (int i = 0; i < 18 && i < spell.length(); i++) {
                int s = Character.getNumericValue(spell.charAt(i));
                if (s >= 1 && s <= 4) votes[i][s] += count;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            int best = 0, bestV = 0;
            for (int s = 1; s <= 4; s++) if (votes[i][s] > bestV) { bestV = votes[i][s]; best = s; }
            sb.append(best == 0 ? "0" : best);
        }
        return sb.toString();
    }

    private Set<Integer> coreExcluded(BuildSignature base) {
        Set<Integer> ex = new HashSet<>(base.coreItems());
        ex.add(base.boots());
        if (base.suppItem() != 0) ex.add(base.suppItem());
        return ex;
    }

    private void fill(List<Integer> target, Set<Integer> excluded, Map<Integer, Integer> freq, int limit) {
        freq.entrySet().stream()
                .filter(e -> !excluded.contains(e.getKey()))
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .takeWhile(id -> target.size() < limit)
                .forEach(id -> { target.add(id); excluded.add(id); });
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
        try { return BuildSignature.decode(key).fullBuildItems().size(); } catch (Exception e) { return 0; }
    }
}
