package com.safjnest.lol.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.model.match.Participant;

public final class ParticipantBuildCodec {

    private ParticipantBuildCodec() {}

    public static void apply(Participant participant, String buildJson) {
        if (buildJson == null || buildJson.isEmpty()) return;
        apply(participant, new JSONObject(buildJson));
    }

    public static void apply(Participant participant, JSONObject build) {
        if (build == null) return;

        applyItems(participant, build);
        applySummonerSpells(participant, build);
        applyRunes(participant, build);
        applySkillOrder(participant, build);
        applyAugments(participant, build);
        applyBuildPath(participant, build);
    }

    public static JSONObject toJson(Participant p) {
        JSONObject build = new JSONObject();

        JSONObject items = new JSONObject();
        items.put("0", p.item0);
        items.put("1", p.item1);
        items.put("2", p.item2);
        items.put("3", p.item3);
        items.put("4", p.item4);
        items.put("5", p.item5);
        items.put("6", p.item6);
        build.put("items", items);

        JSONArray spells = new JSONArray();
        spells.put(p.summonerSpell1);
        spells.put(p.summonerSpell2);
        build.put("summoner_spells", spells);

        JSONObject runes = new JSONObject();
        runes.put("primary",   new JSONArray(p.primaryRunes));
        runes.put("secondary", new JSONArray(p.secondaryRunes));
        runes.put("stats",     new JSONArray(p.statsRunes));
        build.put("runes", runes);

        build.put("skill_order", new JSONArray(p.skillOrder));
        build.put("augments",    new JSONArray(p.augments));

        JSONObject buildPath = new JSONObject();
        buildPath.put("starter", new JSONArray(p.starterItems));
        buildPath.put("build",   new JSONArray(p.buildPath));
        buildPath.put("boots",   p.boots);
        buildPath.put("support_item", p.supportItem);
        build.put("build", buildPath);

        return build;
    }

    private static void applyItems(Participant p, JSONObject build) {
        if (!build.has("items")) return;
        JSONObject items = build.getJSONObject("items");
        p.item0 = items.optInt("0", 0);
        p.item1 = items.optInt("1", 0);
        p.item2 = items.optInt("2", 0);
        p.item3 = items.optInt("3", 0);
        p.item4 = items.optInt("4", 0);
        p.item5 = items.optInt("5", 0);
        p.item6 = items.optInt("6", 0);
    }

    private static void applySummonerSpells(Participant p, JSONObject build) {
        if (!build.has("summoner_spells")) return;
        JSONArray spells = build.getJSONArray("summoner_spells");
        if (spells.length() > 0) p.summonerSpell1 = spells.optInt(0, 0);
        if (spells.length() > 1) p.summonerSpell2 = spells.optInt(1, 0);
    }

    private static void applyRunes(Participant p, JSONObject build) {
        if (!build.has("runes")) return;
        JSONObject runes = build.getJSONObject("runes");

        if (runes.has("primary")) {
            JSONArray primary = runes.getJSONArray("primary");
            for (int i = 0; i < primary.length(); i++) p.primaryRunes.add(primary.optInt(i, 0));
        }
        if (runes.has("secondary")) {
            JSONArray secondary = runes.getJSONArray("secondary");
            for (int i = 0; i < secondary.length(); i++) p.secondaryRunes.add(secondary.optInt(i, 0));
        }
        if (runes.has("stats")) {
            JSONArray stats = runes.getJSONArray("stats");
            for (int i = 0; i < stats.length(); i++) p.statsRunes.add(stats.optInt(i, 0));
        }
    }

    private static void applySkillOrder(Participant p, JSONObject build) {
        if (!build.has("skill_order")) return;
        JSONArray skills = build.getJSONArray("skill_order");
        for (int i = 0; i < skills.length(); i++) p.skillOrder.add(skills.optInt(i, 0));
    }

    private static void applyAugments(Participant p, JSONObject build) {
        if (!build.has("augments")) return;
        JSONArray augs = build.getJSONArray("augments");
        for (int i = 0; i < augs.length(); i++) p.augments.add(augs.optInt(i, 0));
    }

    private static void applyBuildPath(Participant p, JSONObject build) {
        if (!build.has("build")) return;
        JSONObject buildObj = build.optJSONObject("build", new JSONObject());

        if (buildObj.has("starter")) {
            JSONArray starter = buildObj.optJSONArray("starter", new JSONArray());
            for (int i = 0; i < starter.length(); i++) p.starterItems.add(starter.optInt(i, 0));
        }
        if (buildObj.has("build")) {
            JSONArray buildPathArray = buildObj.optJSONArray("build", new JSONArray());
            for (int i = 0; i < buildPathArray.length(); i++) p.buildPath.add(buildPathArray.optInt(i, 0));
        }
        if (buildObj.has("boots")) {
            p.boots = buildObj.optInt("boots", 0);
        }
        if (buildObj.has("support_item")) {
            p.supportItem = buildObj.optInt("support_item", 0);
        }
    }
}
