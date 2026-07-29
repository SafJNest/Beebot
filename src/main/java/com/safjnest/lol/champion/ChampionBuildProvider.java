package com.safjnest.lol.champion;

import com.safjnest.lol.model.Filter;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;

import java.util.List;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ChampionBuildProvider {

    public static final int BATCH_SIZE = 100;

    private ChampionBuildProvider() {}

    public static List<QueryRecord> load(Filter filter) {
        return MongoDB.getChampionBuildsRaw(filter);
    }

    public static void forEach(Filter filter, Consumer<QueryRecord> consumer) {
        if (consumer == null) return;
        forEachBatch(filter, batch -> {
            for (QueryRecord record : batch) consumer.accept(record);
        });
    }

    public static void forEachBatch(Filter filter, Consumer<List<QueryRecord>> consumer) {
        MongoDB.forEachChampionBuildRawBatch(filter, BATCH_SIZE, consumer);
    }

    public static ChampionBuildData.Game parse(QueryRecord record, Filter filter) {
        JSONObject full = json(record.get("build"));
        if (full == null) return null;

        JSONObject buildObject = full.optJSONObject("build");
        JSONArray skillOrder = full.optJSONArray("skill_order");
        if (buildObject == null || buildObject.optJSONArray("build") == null || skillOrder == null) return null;

        BuildSignature signature = BuildSignature.from(
            full,
            skillOrder,
            full.optJSONArray("prismatics"),
            full.optJSONArray("augments"),
            full.optJSONArray("summoner_spells"),
            filter
        );
        JSONObject runesObject = full.optJSONObject("runes");
        RuneSignature runes = runesObject == null ? null : RuneSignature.from(runesObject);
        return signature == null ? null : new ChampionBuildData.Game(signature, runes, record.getAsBoolean("win"));
    }

    private static JSONObject json(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return new JSONObject(raw); }
        catch (RuntimeException ignored) { return null; }
    }
}
