package com.safjnest.lol.champion;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.utils.MatchMemoryUtils;
import com.safjnest.lol.utils.ChampionBuildTimelineUtils;
import com.safjnest.lol.utils.MatchupTimelineUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ChampionBuildProvider {

    public static final int BATCH_SIZE = 100;

    private ChampionBuildProvider() {}

    public static void forEachBatch(Filter filter, Consumer<List<QueryRecord>> consumer) {
        MongoDB.forEachChampionBuildRawBatch(filter, BATCH_SIZE, consumer);
    }

    public static void forEachArenaBatch(Filter filter, Consumer<List<Match>> consumer) {
        MongoDB.forEachChampionArenaMatchBatch(filter, BATCH_SIZE, consumer);
    }

    public static ChampionBuildData.Game parse(QueryRecord record, Filter filter) {
        return parse(record, filter, null);
    }

    public static ChampionBuildData.Game parse(QueryRecord record, Filter filter, Map<String, Integer> rejections) {
        JSONObject full = json(record.get("build"));
        if (full == null) {
            countRejection(rejections, "invalid_json");
            return null;
        }
        try {
            JSONObject timeline = full.optJSONObject("timeline");
            if (!MatchupTimelineUtils.hasTimeline(timeline)) {
                countRejection(rejections, "unusable_timeline");
                return null;
            }
            JSONObject buildObject = full.optJSONObject("build");
            JSONArray skillOrder = full.optJSONArray("skill_order");
            if (buildObject == null || buildObject.optJSONArray("build") == null) {
                countRejection(rejections, "missing_build_fields");
                return null;
            }

            String puuid = record.get("puuid");
            List<ChampionBuildTimelineUtils.ItemEvent> itemEvents = ChampionBuildTimelineUtils.itemEvents(timeline, puuid);
            List<ChampionBuildTimelineUtils.ItemEvent> starterEvents = ChampionBuildTimelineUtils.starterItemEvents(timeline, puuid);
            List<ChampionBuildTimelineUtils.SkillUpgrade> skillUpgrades = ChampionBuildTimelineUtils.skillUpgrades(timeline, puuid);

            BuildSignature signature = BuildSignature.from(
                full,
                skillOrder,
                full.optJSONArray("augments"),
                full.optJSONArray("summoner_spells"),
                filter,
                itemEvents,
                starterEvents,
                skillUpgrades
            );
            JSONObject runesObject = full.optJSONObject("runes");
            RuneSignature runes = runesObject == null ? null : RuneSignature.from(runesObject);
            Map<Integer, Long> itemTimes = new LinkedHashMap<>();
            for (ChampionBuildTimelineUtils.ItemEvent item : itemEvents)
                itemTimes.put(item.itemId(), item.timestampMillis());
            if (signature == null) {
                countRejection(rejections, "invalid_final_build");
                return null;
            }
            return new ChampionBuildData.Game(signature, runes, record.getAsBoolean("win"),
                buildObject.optInt("role_bound", 0), Map.copyOf(itemTimes), skillUpgrades);
        } finally {
            MatchMemoryUtils.release(full);
        }
    }

    private static void countRejection(Map<String, Integer> rejections, String reason) {
        if (rejections != null) rejections.merge(reason, 1, Integer::sum);
    }

    private static JSONObject json(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return new JSONObject(raw); }
        catch (RuntimeException ignored) { return null; }
    }
}
