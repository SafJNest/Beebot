package com.safjnest.lol.champion;

import com.safjnest.lol.model.Filter;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ChampionBuildProvider {

    private ChampionBuildProvider() {}

    public static List<QueryRecord> load(Filter filter) {
        return MongoDB.getChampionBuildsRaw(filter);
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
