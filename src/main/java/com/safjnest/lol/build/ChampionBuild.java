package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public record ChampionBuild(
        BuildFilter filter,
        ChampionBuildService.Strategy strategy,
        List<Integer> starter,
        int boots,
        int suppItem,
        List<Integer> core,
        List<List<SlotOption>> slots,
        String spellOrder,
        RuneSignature runes,
        int games,
        double winrate
) {

    public record SlotOption(int itemId, int matches, double winrate) {
        public JSONObject toJson() {
            return new JSONObject().put("itemId", itemId).put("matches", matches).put("winrate", winrate);
        }
        public static SlotOption fromJson(JSONObject j) {
            return new SlotOption(j.getInt("itemId"), j.getInt("matches"), j.getDouble("winrate"));
        }
    }

    public String toBase64() {
        JSONObject json = new JSONObject();
        json.put("starter",    new JSONArray(starter));
        json.put("boots",      boots);
        json.put("suppItem",   suppItem);
        json.put("core",       new JSONArray(core));
        json.put("spellOrder", spellOrder);
        json.put("games",      games);
        json.put("winrate",    winrate);

        JSONArray slotsArr = new JSONArray();
        for (List<SlotOption> slotOptions : slots) {
            JSONArray optArr = new JSONArray();
            slotOptions.forEach(o -> optArr.put(o.toJson()));
            slotsArr.put(optArr);
        }
        json.put("slots", slotsArr);

        if (runes != null) {
            json.put("runes", new JSONObject()
                    .put("primaryTree",    runes.primaryTree())
                    .put("keystone",       runes.keystone())
                    .put("primaryRunes",   runes.primaryRunes())
                    .put("secondaryTree",  runes.secondaryTree())
                    .put("secondaryRunes", runes.secondaryRunes())
                    .put("statShards",     runes.statShards()));
        }

        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static ChampionBuild fromBase64(String b64, BuildFilter filter, ChampionBuildService.Strategy strategy) {
        JSONObject json = new JSONObject(new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8));

        List<Integer> starter = toIntList(json.getJSONArray("starter"));
        List<Integer> core    = toIntList(json.getJSONArray("core"));

        JSONArray slotsArr = json.getJSONArray("slots");
        List<List<SlotOption>> slots = new ArrayList<>();
        for (int i = 0; i < slotsArr.length(); i++) {
            JSONArray optArr = slotsArr.getJSONArray(i);
            List<SlotOption> options = new ArrayList<>();
            for (int j = 0; j < optArr.length(); j++) options.add(SlotOption.fromJson(optArr.getJSONObject(j)));
            slots.add(options);
        }

        RuneSignature runes = null;
        if (json.has("runes")) {
            JSONObject r = json.getJSONObject("runes");
            runes = new RuneSignature(
                    r.getInt("primaryTree"), r.getInt("keystone"), r.getString("primaryRunes"),
                    r.getInt("secondaryTree"), r.getString("secondaryRunes"), r.getString("statShards"));
        }

        return new ChampionBuild(filter, strategy, starter,
                json.getInt("boots"), json.getInt("suppItem"), core, slots,
                json.getString("spellOrder"), runes, json.getInt("games"), json.getDouble("winrate"));
    }

    private static List<Integer> toIntList(JSONArray arr) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.getInt(i));
        return list;
    }

    public void print() {
        System.out.printf("=== ChampionBuild === games=%d winrate=%.1f%%%n", games, winrate * 100);
        System.out.println("starter=" + starter);
        System.out.println("boots=" + boots + (suppItem != 0 ? " | supp=" + suppItem : ""));
        System.out.println("core=" + core);
        for (int i = 0; i < slots.size(); i++) {
            System.out.println("slot " + (i + 4) + ":");
            for (SlotOption opt : slots.get(i))
                System.out.printf("  item=%-6d  %d matches  %.1f%% WR%n", opt.itemId(), opt.matches(), opt.winrate() * 100);
        }
        System.out.println("spellOrder=" + spellOrder);
        if (runes != null) {
            System.out.println("keystone=" + runes.keystone() + " | tree=" + runes.primaryTree());
            System.out.println("primary=" + runes.primaryRuneItems());
            System.out.println("secondary=" + runes.secondaryTree() + " " + runes.secondaryRuneItems());
            System.out.println("shards=" + runes.statShardItems());
        }
    }

    // debug helper
    public static String a(List<Integer> ids) {
        return ids.toString();
    }
}
