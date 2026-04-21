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
        List<Integer> spellOrder,
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
        json.put("starter", BuildUtils.intListToJsonArray(starter));
        json.put("core", BuildUtils.intListToJsonArray(core));
        json.put("spellOrder", BuildUtils.intListToJsonArray(spellOrder));
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

        JSONArray prismaticsOuter = new JSONArray();
        for (List<SlotOption> row : prismatics) {
            JSONArray inner = new JSONArray();
            row.forEach(o -> inner.put(o.toJson()));
            prismaticsOuter.put(inner);
        }
        json.put("prismatics", prismaticsOuter);

        JSONArray augmentsArr = new JSONArray();
        augments.forEach(a -> augmentsArr.put(a.toJson()));
        json.put("augments", augmentsArr);

        if (runes != null) {
            json.put("runes", new JSONObject()
                    .put("primaryTree",    runes.primaryTree())
                    .put("keystone",       runes.keystone())
                    .put("primaryRunes",   BuildUtils.intListToJsonArray(runes.primaryRunes()))
                    .put("secondaryTree",  runes.secondaryTree())
                    .put("secondaryRunes", BuildUtils.intListToJsonArray(runes.secondaryRunes()))
                    .put("statShards",     BuildUtils.intListToJsonArray(runes.statShards())));
        }

        return BuildUtils.toBase64(json.toString());
    }

    public static ChampionBuild decode(String b64, BuildFilter filter) {
        JSONObject json = new JSONObject(BuildUtils.fromBase64(b64));

        List<Integer> starter = BuildUtils.jsonArrayToIntList(json.getJSONArray("starter"));
        List<Integer> core = BuildUtils.jsonArrayToIntList(json.getJSONArray("core"));
        List<Integer> spellOrder = decodeSpellOrder(json);

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
                    r.getInt("primaryTree"),
                    r.getInt("keystone"),
                    runeListField(r, "primaryRunes"),
                    r.getInt("secondaryTree"),
                    runeListField(r, "secondaryRunes"),
                    runeListField(r, "statShards"));
        }

        JSONArray bootsArr = json.getJSONArray("boots");
        List<SlotOption> boots = new ArrayList<>();
        for (int i = 0; i < bootsArr.length(); i++) {
            boots.add(SlotOption.fromJson(bootsArr.getJSONObject(i)));
        }

        JSONArray suppItemArr = json.optJSONArray("suppItems");
        if (suppItemArr == null) suppItemArr = json.optJSONArray("suppItem");
        if (suppItemArr == null) suppItemArr = new JSONArray();
        List<SlotOption> suppItem = new ArrayList<>();
        for (int i = 0; i < suppItemArr.length(); i++) {
            suppItem.add(SlotOption.fromJson(suppItemArr.getJSONObject(i)));
        }

        List<List<SlotOption>> prismatics = new ArrayList<>();
        if (json.has("prismatics")) {
            JSONArray prismaticsArr = json.getJSONArray("prismatics");
            if (prismaticsArr.length() > 0 && prismaticsArr.opt(0) instanceof JSONObject) {
                List<SlotOption> legacy = new ArrayList<>();
                for (int i = 0; i < prismaticsArr.length(); i++)
                    legacy.add(SlotOption.fromJson(prismaticsArr.getJSONObject(i)));
                prismatics.add(legacy);
            } else {
                for (int i = 0; i < prismaticsArr.length(); i++) {
                    JSONArray optArr = prismaticsArr.optJSONArray(i);
                    List<SlotOption> options = new ArrayList<>();
                    if (optArr != null)
                        for (int j = 0; j < optArr.length(); j++) options.add(SlotOption.fromJson(optArr.getJSONObject(j)));
                    prismatics.add(options);
                }
            }
        }

        List<SlotOption> augments = new ArrayList<>();
        if (json.has("augments")) {
            JSONArray augmentsArr = json.getJSONArray("augments");
            for (int i = 0; i < augmentsArr.length(); i++) {
                augments.add(SlotOption.fromJson(augmentsArr.getJSONObject(i)));
            }
        }

        return new ChampionBuild(filter, starter,
                boots, suppItem, core, slots, prismatics, augments,
                spellOrder, runes, json.getInt("games"), json.getDouble("winrate"));
    }

    private static List<Integer> decodeSpellOrder(JSONObject json) {
        if (!json.has("spellOrder")) {
            List<Integer> z = new ArrayList<>(18);
            for (int i = 0; i < 18; i++) z.add(0);
            return z;
        }
        Object so = json.get("spellOrder");
        if (so instanceof JSONArray a) return normalizeSpellOrder(BuildUtils.jsonArrayToIntList(a));
        if (so instanceof String s) {
            List<Integer> o = new ArrayList<>(18);
            for (int i = 0; i < 18; i++) {
                int v = i < s.length() ? Character.getNumericValue(s.charAt(i)) : 0;
                o.add(v >= 1 && v <= 4 ? v : 0);
            }
            return o;
        }
        List<Integer> z = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) z.add(0);
        return z;
    }

    private static List<Integer> normalizeSpellOrder(List<Integer> raw) {
        List<Integer> o = new ArrayList<>(18);
        for (int i = 0; i < 18; i++) {
            int v = i < raw.size() ? raw.get(i) : 0;
            o.add(v >= 1 && v <= 4 ? v : 0);
        }
        return o;
    }

    private static List<Integer> runeListField(JSONObject r, String key) {
        if (!r.has(key)) return List.of();
        Object o = r.get(key);
        if (o instanceof JSONArray a) return BuildUtils.jsonArrayToIntList(a);
        if (o instanceof String s) return BuildUtils.parseDashList(s);
        return List.of();
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
            System.out.println("keystone=" + runes.keystone() + " | primaryTree=" + runes.primaryTree());
            System.out.println("primaryRunes=" + runes.primaryRunes());
            System.out.println("secondaryTree=" + runes.secondaryTree() + " secondaryRunes=" + runes.secondaryRunes());
            System.out.println("statShards=" + runes.statShards());
        }
    }

    public List<String> getSkillOrder() {
        List<String> skillOrder = new ArrayList<>();
        for (int i = 0; i < 18 && i < spellOrder.size(); i++) {
            skillOrder.add(String.valueOf(spellOrder.get(i)));
        }
        while (skillOrder.size() < 18) skillOrder.add("0");
        return skillOrder;
    }

}
