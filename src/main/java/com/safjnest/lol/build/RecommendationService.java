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

    public enum Strategy {
        HIGHEST_WR, MOST_USED
    }

    public record Recommendation(
            int championId,
            String lane,
            String role,
            Strategy strategy,
            BuildSignature build,
            RuneSignature runes,
            int games,
            double winrate) {
    }

    private static final int MIN_GAMES = 5;
    private static final int SUGGESTIONS_LIMIT = 6;

    /** Per-row reasons why a participant row did not contribute to stats (build vs rune pass). */
    private static final class StatsDiagnostics {
        int rowsFromQuery;
        int skippedNullBuildColumn;
        int skippedBadJson;
        int skippedMissingBuildOrSkillOrder;
        int skippedIncompleteBuildSig;
        int skippedMissingRunes;
        int skippedIncompleteRuneSig;
        int rowsAccepted;

        @Override
        public String toString() {
            return String.format(
                    "rowsFromQuery=%d, accepted=%d, skip{nullBuildCol=%d, badJson=%d, noBuildOrSkillOrder=%d, incompleteBuildSig=%d, noRunes=%d, incompleteRuneSig=%d}",
                    rowsFromQuery, rowsAccepted, skippedNullBuildColumn, skippedBadJson,
                    skippedMissingBuildOrSkillOrder, skippedIncompleteBuildSig,
                    skippedMissingRunes, skippedIncompleteRuneSig);
        }
    }

    private record StatsResult(
            Map<String, int[]> stats,
            StatsDiagnostics diagnostics,
            Map<String, String> representativeBuildByGroup,
            Map<String, Map<Integer, Integer>> itemFrequencyByGroup) {
    }

    public Recommendation get(int championId, String lane, String role, Strategy strategy, Connection conn)
            throws Exception {
        Recommendation cached = retrieve(championId, lane, role, strategy, conn);
        if (cached != null)
            return cached;

        Recommendation computed = analyze(championId, lane, role, strategy, conn);
        save(computed, conn);
        return computed;
    }

    private Recommendation retrieve(int championId, String lane, String role, Strategy strategy, Connection conn) {
        // TODO: SELECT FROM recommendations WHERE champion_id=? AND lane=? AND role=?
        // AND strategy=?
        // BuildSignature.decode(rs.getString("build_key")),
        // RuneSignature.decode(rs.getString("rune_key"))
        return null;
    }

    private void save(Recommendation r, Connection conn) {
        // TODO: INSERT INTO recommendations (champion_id, lane, role, strategy,
        // build_key, rune_key, games, winrate, updated_at)
        // r.build().toKey(), r.runes().toKey()
    }

    private Recommendation analyze(int championId, String lane, String role, Strategy strategy, Connection conn)
            throws Exception {
        log.info(
                "[Recommendation] analyze championId={} lane={} role={} strategy={} MIN_GAMES={} (role is not used in SQL yet — only champion+lane)",
                championId, lane, role, strategy, MIN_GAMES);

        StatsResult buildSr = computeStats(championId, lane, role, conn, true);
        StatsResult runeSr = computeStats(championId, lane, role, conn, false);

        Map<String, int[]> buildStats = buildSr.stats();
        Map<String, int[]> runeStats = runeSr.stats();

        log.info("[Recommendation] build pass: {} | distinctKeys={}", buildSr.diagnostics(), buildStats.size());
        log.info("[Recommendation] rune pass: {} | distinctKeys={}", runeSr.diagnostics(), runeStats.size());

        String buildGroupKey = switch (strategy) {
            case HIGHEST_WR -> topKey(buildStats, true);
            case MOST_USED -> topKey(buildStats, false);
        };
        BuildSignature build = null;
        if (buildGroupKey != null) {
            String representativeFullBuildKey = buildSr.representativeBuildByGroup().get(buildGroupKey);
            if (representativeFullBuildKey != null) {
                build = BuildSignature.decode(representativeFullBuildKey);
                Map<Integer, Integer> freq = buildSr.itemFrequencyByGroup().getOrDefault(buildGroupKey, Collections.emptyMap());
                Map<Integer, Integer> globalFreq = buildSr.itemFrequencyByGroup().values().stream()
                        .flatMap(m -> m.entrySet().stream())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                Integer::sum));
                build = enrichBuildToSixItems(build, freq, globalFreq);
            }
        }

        RuneSignature runes = switch (strategy) {
            case HIGHEST_WR -> top(runeStats, RuneSignature::decode, true);
            case MOST_USED -> top(runeStats, RuneSignature::decode, false);
        };

        logTopSelection("build", buildStats, strategy == Strategy.HIGHEST_WR, build != null);
        logTopSelection("runes", runeStats, strategy == Strategy.HIGHEST_WR, runes != null);

        int totalGames = buildStats.values().stream().mapToInt(v -> v[0]).sum();
        int[] bs = buildGroupKey != null ? buildStats.getOrDefault(buildGroupKey, new int[] { 0, 0 }) : new int[] { 0, 0 };
        double winrate = bs[0] > 0 ? (double) bs[1] / bs[0] : 0;

        return new Recommendation(championId, lane, role, strategy, build, runes, totalGames, winrate);
    }

    private void logTopSelection(String label, Map<String, int[]> stats, boolean highestWr, boolean selectedNonNull) {
        if (stats.isEmpty()) {
            log.warn("[Recommendation] top({}): no distinct keys — nothing to select", label);
            return;
        }
        long passingFilter = stats.entrySet().stream()
                .filter(e -> !highestWr || e.getValue()[0] >= MIN_GAMES)
                .count();
        int maxGames = stats.values().stream().mapToInt(v -> v[0]).max().orElse(0);
        String sample = stats.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> e.getValue()[0]).reversed())
                .limit(3)
                .map(e -> String.format("%s[g=%d,w=%d]", abbreviateKey(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .collect(Collectors.joining(", "));
        log.info(
                "[Recommendation] top({}): mode={} distinctKeys={} keysPassingFilter={} (need games>={} for HIGHEST_WR) maxGamesAnyKey={} selected={} sampleTopByGames=[{}]",
                label, highestWr ? "HIGHEST_WR" : "MOST_USED", stats.size(), passingFilter, MIN_GAMES, maxGames,
                selectedNonNull ? "yes" : "NULL", sample);
        if (!selectedNonNull && highestWr && maxGames > 0 && maxGames < MIN_GAMES) {
            log.warn(
                    "[Recommendation] top({}): HIGHEST_WR requires every key to have games>={} — your best key has only {} games. Use MOST_USED or lower MIN_GAMES.",
                    label, MIN_GAMES, maxGames);
        }
    }

    private static String abbreviateKey(String base64Key) {
        if (base64Key == null)
            return "null";
        return base64Key.length() <= 24 ? base64Key : base64Key.substring(0, 20) + "…";
    }

    private StatsResult computeStats(int championId, String lane, String role, Connection conn,
            boolean isBuild) throws Exception {

        Map<String, int[]> stats = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> buildVariantsByGroup = new HashMap<>();
        Map<String, Map<Integer, Integer>> itemFrequencyByGroup = new HashMap<>();
        StatsDiagnostics d = new StatsDiagnostics();

        String sql = "SELECT p.win, p.build FROM participant p " +
                "JOIN `match` m ON m.id = p.match_id " +
                "WHERE p.champion = ? AND p.lane = ?  ";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, championId);
            ps.setString(2, lane);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    d.rowsFromQuery++;

                    boolean win = rs.getInt("win") == 1;
                    String buildJson = rs.getString("build");
                    if (buildJson == null) {
                        d.skippedNullBuildColumn++;
                        continue;
                    }

                    JSONObject full;
                    try {
                        full = new JSONObject(buildJson);
                    } catch (Exception ex) {
                        d.skippedBadJson++;
                        log.trace("[Recommendation] bad JSON in participant.build (row {}): {}", d.rowsFromQuery, ex.toString());
                        continue;
                    }

                    String key;

                    if (isBuild) {
                        // Build signature now relies only on root.build.build (ordered purchases),
                        // plus build metadata (starter / boots / support_item).
                        JSONObject buildObj = full.optJSONObject("build");
                        JSONArray skillOrder = full.optJSONArray("skill_order");
                        JSONArray buildPath = buildObj != null ? buildObj.optJSONArray("build") : null;

                        if (buildObj == null || buildPath == null || skillOrder == null) {
                            d.skippedMissingBuildOrSkillOrder++;
                            if (d.skippedMissingBuildOrSkillOrder <= 2) {
                                log.debug(
                                        "[Recommendation] sample skip buildPath/skill_order: hasBuild={} hasBuildPath={} hasSkillOrder={} keys={}",
                                        buildObj != null, buildPath != null, skillOrder != null, full.keySet());
                            }
                            continue;
                        }

                        BuildSignature sig = BuildSignature.from(full, skillOrder);
                        if (sig == null) {
                            d.skippedIncompleteBuildSig++;
                            continue;
                        }

                        key = sig.toCoreKey();
                        buildVariantsByGroup
                                .computeIfAbsent(key, k -> new HashMap<>())
                                .merge(sig.toKey(), 1, Integer::sum);
                        Map<Integer, Integer> itemFreq = itemFrequencyByGroup.computeIfAbsent(key, k -> new HashMap<>());
                        for (Integer itemId : sig.fullBuildItems()) {
                            itemFreq.merge(itemId, 1, Integer::sum);
                        }

                    } else {
                        JSONObject runes = full.optJSONObject("runes");
                        if (runes == null) {
                            d.skippedMissingRunes++;
                            continue;
                        }

                        RuneSignature sig = RuneSignature.from(runes);
                        if (sig == null) {
                            d.skippedIncompleteRuneSig++;
                            continue;
                        }

                        key = sig.toKey();
                    }

                    stats.computeIfAbsent(key, k -> new int[2]);
                    stats.get(key)[0]++;
                    if (win)
                        stats.get(key)[1]++;
                    d.rowsAccepted++;
                }
            }
        }
        catch (Exception e) {
            log.error("[Recommendation] computeStats failed (isBuild={})", isBuild, e);
            throw e;
        }

        Map<String, String> representativeBuildByGroup = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> groupEntry : buildVariantsByGroup.entrySet()) {
            String representative = groupEntry.getValue().entrySet().stream()
                    .max(Comparator
                            .<Map.Entry<String, Integer>>comparingInt(e -> fullBuildItemCountFromKey(e.getKey()))
                            .thenComparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (representative != null) {
                representativeBuildByGroup.put(groupEntry.getKey(), representative);
            }
        }

        return new StatsResult(stats, d, representativeBuildByGroup, itemFrequencyByGroup);
    }

    private BuildSignature enrichBuildToSixItems(
            BuildSignature base,
            Map<Integer, Integer> frequencyByItem,
            Map<Integer, Integer> globalFrequencyByItem) {
        if (base == null) {
            return null;
        }

        List<Integer> full = new ArrayList<>(base.fullBuildItems());
        Set<Integer> present = new HashSet<>(full);
        List<Integer> core = base.coreItems();
        Set<Integer> excluded = new HashSet<>(core);
        if (base.boots() != 0) {
            excluded.add(base.boots());
        }
        if (base.suppItem() != 0) {
            excluded.add(base.suppItem());
        }

        List<Integer> rankedMissing = frequencyByItem.entrySet().stream()
                .filter(e -> !present.contains(e.getKey()))
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        for (Integer itemId : rankedMissing) {
            if (full.size() >= 6) {
                break;
            }
            full.add(itemId);
            present.add(itemId);
        }

        if (full.size() < 6) {
            List<Integer> globalRankedMissing = globalFrequencyByItem.entrySet().stream()
                    .filter(e -> !present.contains(e.getKey()))
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
            for (Integer itemId : globalRankedMissing) {
                if (full.size() >= 6) {
                    break;
                }
                full.add(itemId);
                present.add(itemId);
            }
        }

        // Suggestions must be alternatives, not overlapping with full build.
        Set<Integer> suggestionExcluded = new HashSet<>(excluded);
        suggestionExcluded.addAll(present);
        List<Integer> suggestions = new ArrayList<>();

        List<Integer> rankedSuggestionsFromGroup = frequencyByItem.entrySet().stream()
                .filter(e -> !suggestionExcluded.contains(e.getKey()))
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (Integer itemId : rankedSuggestionsFromGroup) {
            if (suggestions.size() >= SUGGESTIONS_LIMIT) {
                break;
            }
            suggestions.add(itemId);
            suggestionExcluded.add(itemId);
        }

        if (suggestions.size() < SUGGESTIONS_LIMIT) {
            List<Integer> rankedSuggestionsGlobal = globalFrequencyByItem.entrySet().stream()
                    .filter(e -> !suggestionExcluded.contains(e.getKey()))
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
            for (Integer itemId : rankedSuggestionsGlobal) {
                if (suggestions.size() >= SUGGESTIONS_LIMIT) {
                    break;
                }
                suggestions.add(itemId);
                suggestionExcluded.add(itemId);
            }
        }

        return new BuildSignature(
                base.starter(),
                base.boots(),
                base.suppItem(),
                base.core(),
                joinIds(full),
                joinIds(suggestions),
                base.spellOrder());
    }

    private String joinIds(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }

    private int fullBuildItemCountFromKey(String buildKey) {
        try {
            BuildSignature s = BuildSignature.decode(buildKey);
            return s.fullBuildItems().size();
        } catch (Exception ignored) {
            return 0;
        }
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
        return stats.entrySet().stream()
                .filter(e -> !byWinrate || e.getValue()[0] >= MIN_GAMES)
                .max(byWinrate
                        ? Comparator.comparingDouble(e -> (double) e.getValue()[1] / e.getValue()[0])
                        : Comparator.comparingInt(e -> e.getValue()[0]))
                .map(e -> decoder.apply(e.getKey()))
                .orElse(null);
    }
}
