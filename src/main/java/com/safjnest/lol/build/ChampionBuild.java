package com.safjnest.lol.build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public record ChampionBuild(
        BuildFilter filter,
        List<Integer> starter,
        List<SlotOption> boots,
        List<SlotOption> suppItems,
        List<Integer> core,
        List<List<SlotOption>> slots,
        List<List<SlotOption>> prismatics,
        List<SlotOption> augments,
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

    public String encode() {
        JSONObject json = new JSONObject();
        json.put("starter", new JSONArray(starter));
        json.put("boots", boots);
        json.put("suppItems", suppItems);
        json.put("core", new JSONArray(core));
        json.put("spellOrder", spellOrder);
        json.put("games", games);
        json.put("winrate", winrate);

        JSONArray slotsArr = new JSONArray();
        for (List<SlotOption> slotOptions : slots) {
            JSONArray optArr = new JSONArray();
            slotOptions.forEach(o -> optArr.put(o.toJson()));
            slotsArr.put(optArr);
        }
        json.put("slots", slotsArr);

        JSONArray bootsArr = new JSONArray();
        boots.forEach(b -> bootsArr.put(b.toJson()));
        json.put("boots", bootsArr);

        JSONArray suppItemsArr = new JSONArray();
        suppItems.forEach(s -> suppItemsArr.put(s.toJson()));
        json.put("suppItems", suppItemsArr);

        if (runes != null) {
            json.put("runes", new JSONObject()
                    .put("primaryTree",    runes.primaryTree())
                    .put("keystone",       runes.keystone())
                    .put("primaryRunes",   runes.primaryRunes())
                    .put("secondaryTree",  runes.secondaryTree())
                    .put("secondaryRunes", runes.secondaryRunes())
                    .put("statShards",     runes.statShards()));
        }

        return BuildUtils.toBase64(json.toString());
    }

    public static ChampionBuild decode(String b64, BuildFilter filter) {
        JSONObject json = new JSONObject(BuildUtils.fromBase64(b64));

        List<Integer> starter = BuildUtils.toIntList(json.getJSONArray("starter"));
        List<Integer> core = BuildUtils.toIntList(json.getJSONArray("core"));

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

        JSONArray bootsArr = json.getJSONArray("boots");
        List<SlotOption> boots = new ArrayList<>();
        for (int i = 0; i < bootsArr.length(); i++) {
            boots.add(SlotOption.fromJson(bootsArr.getJSONObject(i)));
        }

        JSONArray suppItemArr = json.getJSONArray("suppItem");
        List<SlotOption> suppItem = new ArrayList<>();
        for (int i = 0; i < suppItemArr.length(); i++) {
            suppItem.add(SlotOption.fromJson(suppItemArr.getJSONObject(i)));
        }

        JSONArray prismaticsArr = json.getJSONArray("prismatics");
        List<List<SlotOption>> prismatics = new ArrayList<>();
        for (int i = 0; i < prismaticsArr.length(); i++) {
            JSONArray optArr = prismaticsArr.getJSONArray(i);
            List<SlotOption> options = new ArrayList<>();
            for (int j = 0; j < optArr.length(); j++) options.add(SlotOption.fromJson(optArr.getJSONObject(j)));
            prismatics.add(options);
        }

        JSONArray augmentsArr = json.getJSONArray("augments");
        List<SlotOption> augments = new ArrayList<>();
        for (int i = 0; i < augmentsArr.length(); i++) {
            augments.add(SlotOption.fromJson(augmentsArr.getJSONObject(i)));
        }


        return new ChampionBuild(filter, starter,
                boots, suppItem, core, slots, prismatics, augments,
                json.getString("spellOrder"), runes, json.getInt("games"), json.getDouble("winrate"));
    }

    public void print() {
        System.out.printf("=== ChampionBuild === games=%d winrate=%.1f%%%n", games, winrate * 100);
        System.out.println("starter=" + BuildUtils.toItemName(starter));
        for (SlotOption boot : boots) {
            System.out.println("boot=" + BuildUtils.toItemName(boot.itemId()) + " " + boot.matches() + " matches " + boot.winrate() * 100 + "%");
        }
        for (SlotOption suppItem : suppItems) {
            System.out.println("suppItem=" + BuildUtils.toItemName(suppItem.itemId()) + " " + suppItem.matches() + " matches " + suppItem.winrate() * 100 + "%");
        }
        System.out.println("core=" + BuildUtils.toItemName(core));
        for (int i = 0; i < slots.size(); i++) {
            System.out.println("slot " + (i + 4) + ":");
            for (SlotOption opt : slots.get(i))
                System.out.printf("  item=%-6s  %d matches  %.1f%% WR%n", BuildUtils.toItemName(opt.itemId()), opt.matches(), opt.winrate() * 100);
        }
        for (List<SlotOption> prismatic : prismatics) {
            System.out.println("prismatic:");
            for (SlotOption opt : prismatic)
                System.out.printf("  item=%-6s  %d matches  %.1f%% WR%n", BuildUtils.toItemName(opt.itemId()), opt.matches(), opt.winrate() * 100);
        }

        System.out.println("augment:");
        for (SlotOption opt : augments)
            System.out.printf("  item=%-6s  %d matches  %.1f%% WR%n", BuildUtils.toAugmentName(opt.itemId()), opt.matches(), opt.winrate() * 100);
        
        System.out.println("spellOrder=" + spellOrder);
        if (runes != null) {
            System.out.println("keystone=" + runes.keystone() + " | tree=" + runes.primaryTree());
            System.out.println("primary=" + BuildUtils.toItemName(runes.primaryRuneItems()));
            System.out.println("secondary=" + runes.secondaryTree() + " " + BuildUtils.toItemName(runes.secondaryRuneItems()));
            System.out.println("shards=" + BuildUtils.toItemName(runes.statShardItems()));
        }
    }

    public List<String> getSkillOrder() {
        List<String> skillOrder = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            skillOrder.add(String.valueOf(spellOrder.charAt(i)));
        }
        return skillOrder;
    }

}
