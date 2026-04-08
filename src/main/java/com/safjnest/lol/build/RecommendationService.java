package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final int MIN_GAMES       = 5;
    private static final int FULL_BUILD_SIZE = 6;
    private static final int SUGGESTIONS_MAX = 3;

    public enum Strategy { HIGHEST_WR, MOST_USED }

    public record Recommendation(
            int championId,
            String lane,
            String role,
            Strategy strategy,
            BuildSignature build,
            RuneSignature runes,
            int games,
            double winrate
    ) {}

    private record StatsResult(
            Map<String, int[]> stats,
            Map<String, String> representativeByGroup,
            Map<String, Map<Integer, Integer>> itemFreqByGroup
    ) {}

    // -------------------------------------------------------------------------

    public Recommendation get(int championId, String lane, String role, Strategy strategy, Connection conn) throws Exception {
        Recommendation cached = retrieve(championId, lane, role, strategy, conn);
        if (cached != null) return cached;

        Recommendation computed = analyze(championId, lane, role, strategy, conn);
        save(computed, conn);
        return computed;
    }

    private Recommendation retrieve(int championId, String lane, String role, Strategy strategy, Connection conn) {
        // TODO: SELECT FROM recommendations WHERE champion_id=? AND lane=? AND role=? AND strategy=?
        // BuildSignature.decode(rs.getString("build_key")), RuneSignature.decode(rs.getString("rune_key"))
        return null;
    }

    private void save(Recommendation r, Connection conn) {
        // TODO: INSERT INTO recommendations (champion_id, lane, role, strategy, build_key, rune_key, games, winrate, updated_at)
        // r.build().toKey(), r.runes().toKey()
    }

    // -------------------------------------------------------------------------

    private Recommendation analyze(int championId, String lane, String role, Strategy strategy, Connection conn) throws Exception {
        StatsResult buildSr = computeStats(championId, lane, conn, true);
        StatsResult runeSr  = computeStats(championId, lane, conn, false);

        log.info("[Recommendation] champion={} lane={} role={} strategy={} buildKeys={} runeKeys={}",
                championId, lane, role, strategy, buildSr.stats().size(), runeSr.stats().size());

        String buildGroupKey = topKey(buildSr.stats(), strategy == Strategy.HIGHEST_WR);
        BuildSignature build = resolveBuild(buildGroupKey, buildSr);

        RuneSignature runes = top(runeSr.stats(), RuneSignature::decode, strategy == Strategy.HIGHEST_WR);

        int totalGames = buildSr.stats().values().stream().mapToInt(v -> v[0]).sum();
        int[] bs       = buildGroupKey != null ? buildSr.stats().getOrDefault(buildGroupKey, new int[]{0, 0}) : new int[]{0, 0};
        double winrate = bs[0] > 0 ? (double) bs[1] / bs[0] : 0;

        return new Recommendation(championId, lane, role, strategy, build, runes, totalGames, winrate);
    }

    private BuildSignature resolveBuild(String groupKey, StatsResult sr) {
        if (groupKey == null) return null;

        String representativeKey = sr.representativeByGroup().get(groupKey);
        if (representativeKey == null) return null;

        BuildSignature base = BuildSignature.decode(representativeKey);

        Map<Integer, Integer> groupFreq  = sr.itemFreqByGroup().getOrDefault(groupKey, Collections.emptyMap());
        Map<Integer, Integer> globalFreq = sr.itemFreqByGroup().values().stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

        return enrichBuild(base, groupFreq, globalFreq);
    }

    // -------------------------------------------------------------------------

    private StatsResult computeStats(int championId, String lane, Connection conn, boolean isBuild) throws Exception {
        Map<String, int[]> stats                        = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> variantsByGroup  = new HashMap<>();
        Map<String, Map<Integer, Integer>> itemFreqByGroup = new HashMap<>();

        String sql = "SELECT p.win, p.build FROM participant p " +
                     "JOIN `match` m ON m.id = p.match_id " +
                     "WHERE p.champion = ? AND p.lane = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, championId);
            ps.setString(2, lane);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean win = rs.getInt("win") == 1;
                    String rawJson = rs.getString("build");
                    if (rawJson == null) continue;

                    JSONObject full;
                    try { full = new JSONObject(rawJson); }
                    catch (Exception ex) { continue; }

                    String key = isBuild
                            ? extractBuildKey(full, variantsByGroup, itemFreqByGroup)
                            : extractRuneKey(full);

                    if (key == null) continue;

                    stats.computeIfAbsent(key, k -> new int[2]);
                    stats.get(key)[0]++;
                    if (win) stats.get(key)[1]++;
                }
            }
        }

        Map<String, String> representativeByGroup = resolveRepresentatives(variantsByGroup);
        return new StatsResult(stats, representativeByGroup, itemFreqByGroup);
    }

    private String extractBuildKey(JSONObject full,
                                    Map<String, Map<String, Integer>> variantsByGroup,
                                    Map<String, Map<Integer, Integer>> itemFreqByGroup) {
        JSONObject buildObj  = full.optJSONObject("build");
        JSONArray skillOrder = full.optJSONArray("skill_order");
        if (buildObj == null || skillOrder == null || buildObj.optJSONArray("build") == null) return null;

        BuildSignature sig = BuildSignature.from(full, skillOrder);
        if (sig == null) return null;

        String coreKey = sig.toCoreKey();
        variantsByGroup.computeIfAbsent(coreKey, k -> new HashMap<>()).merge(sig.toKey(), 1, Integer::sum);
        Map<Integer, Integer> freq = itemFreqByGroup.computeIfAbsent(coreKey, k -> new HashMap<>());
        sig.fullBuildItems().forEach(id -> freq.merge(id, 1, Integer::sum));

        return coreKey;
    }

    private String extractRuneKey(JSONObject full) {
        JSONObject runes = full.optJSONObject("runes");
        if (runes == null) return null;
        RuneSignature sig = RuneSignature.from(runes);
        return sig != null ? sig.toKey() : null;
    }

    private Map<String, String> resolveRepresentatives(Map<String, Map<String, Integer>> variantsByGroup) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : variantsByGroup.entrySet()) {
            entry.getValue().entrySet().stream()
                    .max(Comparator
                            .<Map.Entry<String, Integer>>comparingInt(e -> itemCountFromKey(e.getKey()))
                            .thenComparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .ifPresent(rep -> result.put(entry.getKey(), rep));
        }
        return result;
    }

    // -------------------------------------------------------------------------

    private BuildSignature enrichBuild(BuildSignature base,
                                        Map<Integer, Integer> groupFreq,
                                        Map<Integer, Integer> globalFreq) {
        List<Integer> full    = new ArrayList<>(base.fullBuildItems());
        Set<Integer> present  = new HashSet<>(full);
        Set<Integer> excluded = buildExcluded(base);

        fill(full, present, groupFreq,  FULL_BUILD_SIZE);
        fill(full, present, globalFreq, FULL_BUILD_SIZE);

        List<Integer> suggestions = new ArrayList<>();
        Set<Integer> suggExcluded = new HashSet<>(excluded);
        suggExcluded.addAll(present);

        fill(suggestions, suggExcluded, groupFreq,  SUGGESTIONS_MAX);
        fill(suggestions, suggExcluded, globalFreq, SUGGESTIONS_MAX);

        // --- validations ---
        full        = full.subList(0, Math.min(full.size(), FULL_BUILD_SIZE));
        suggestions = suggestions.subList(0, Math.min(suggestions.size(), SUGGESTIONS_MAX));
        suggestions.removeAll(new HashSet<>(full));

        return new BuildSignature(
                base.starter(), base.boots(), base.suppItem(), base.core(),
                RuneSignature.joinInts(full),
                RuneSignature.joinInts(suggestions),
                base.spellOrder()
        );
    }

    private Set<Integer> buildExcluded(BuildSignature base) {
        Set<Integer> excluded = new HashSet<>(base.coreItems());
        if (base.boots() != 0) excluded.add(base.boots());
        if (base.suppItem() != 0) excluded.add(base.suppItem());
        return excluded;
    }

    private void fill(List<Integer> target, Set<Integer> excluded, Map<Integer, Integer> freq, int limit) {
        freq.entrySet().stream()
                .filter(e -> !excluded.contains(e.getKey()))
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .takeWhile(id -> target.size() < limit)
                .forEach(id -> { target.add(id); excluded.add(id); });
    }

    // -------------------------------------------------------------------------

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
        return stats.entrySet().stream()
                .filter(e -> !byWinrate || e.getValue()[0] >= MIN_GAMES)
                .max(byWinrate
                        ? Comparator.comparingDouble(e -> (double) e.getValue()[1] / e.getValue()[0])
                        : Comparator.comparingInt(e -> e.getValue()[0]))
                .map(e -> decoder.apply(e.getKey()))
                .orElse(null);
    }

    private int itemCountFromKey(String buildKey) {
        try { return BuildSignature.decode(buildKey).fullBuildItems().size(); }
        catch (Exception ignored) { return 0; }
    }
}
