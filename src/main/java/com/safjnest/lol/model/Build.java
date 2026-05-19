package com.safjnest.lol.model;

import com.safjnest.lol.build.RuneSignature;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.util.KryoUtils;

import java.util.ArrayList;
import java.util.List;

public record Build(
    Filter filter,
    List<Integer> starter,
    List<SlotOption> boots,
    List<SlotOption> suppItems,
    List<Integer> core,
    List<List<SlotOption>> slots,
    List<List<SlotOption>> prismatics,
    List<SlotOption> summonerSpells,
    List<SlotOption> augments,
    List<Integer> spellOrder,
    RuneSignature runes,
    int games,
    double winrate
) {

    public record SlotOption(int itemId, int matches, double winrate) { 
        public int getSpell1() {
            return itemId / 100;
        }
        public int getSpell2() {
            return itemId % 100;
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }

        public String prettyMatches() {
            return String.format("%d", matches);
        }

    }

    public String encode() {
        return KryoUtils.encode(this);
    }

    public static Build decode(String b64) {
        return KryoUtils.decode(b64, Build.class);
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

        System.out.println("summonerSpells:");
        for (SlotOption opt : summonerSpells) {
            int key = opt.itemId();
            System.out.printf("  item=%-6s  %d matches  %.1f%% WR%n", (key / 100) + "-" + (key % 100), opt.matches(), opt.winrate() * 100);
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

    public int getKeystone() {
        return runes.keystone();
    }

    public int getPrimaryTree() {
        return runes.primaryTree();
    }

    public int getSecondaryTree() {
        return runes.secondaryTree();
    }

    public List<Integer> getPrimaryRunes() {
        return runes.primaryRunes();
    }

    public List<Integer> getSecondaryRunes() {
        return runes.secondaryRunes();
    }

    public List<Integer> getStatShards() {
        return runes.statShards();
    }

    public int getOffense() {
        return runes.statShards().get(0);
    }

    public int getFlex() {
        return runes.statShards().get(1);
    }

    public int getDefense() {
        return runes.statShards().get(2);
    }

}
