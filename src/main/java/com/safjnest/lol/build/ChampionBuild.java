package com.safjnest.lol.build;

import java.util.List;
import java.util.stream.Collectors;

import com.safjnest.lol.LeagueHandler;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

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

    public record SlotOption(int itemId, int matches, double winrate) {}

    public void print() {
        System.out.printf("=== ChampionBuild === games=%d winrate=%.1f%%%n", games, winrate * 100);
        System.out.println("starter=" + toItemNames(starter));
        System.out.println("boots=" + boots + (suppItem != 0 ? " | supp=" + suppItem : ""));
        System.out.println("core=" + toItemNames(core));
        for (int i = 0; i < slots.size(); i++) {
            System.out.println("slot " + (i + 4) + ":");
            for (SlotOption opt : slots.get(i))
                System.out.printf("  item=%-6s  %d matches  %.1f%% WR%n", LeagueHandler.itemsMap.get(opt.itemId()).getName(), opt.matches(), opt.winrate() * 100);
        }
        System.out.println("spellOrder=" + spellOrder);
        if (runes != null) {
            System.out.println("keystone=" + runes.keystone() + " | tree=" + runes.primaryTree());
            System.out.println("primary=" + runes.primaryRuneItems());
            System.out.println("secondary=" + runes.secondaryTree() + " " + runes.secondaryRuneItems());
            System.out.println("shards=" + runes.statShardItems());
        }
    }

    public String toItemNames(List<Integer> itemIds) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < itemIds.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(LeagueHandler.itemsMap.get(itemIds.get(i)).getName());
        }
        sb.append("]");
        return sb.toString();
    }

    public static String a(List<Integer> itemIds) {
        return itemIds.stream().map(LeagueHandler.itemsMap::get).map(Item::getName).collect(Collectors.joining(", "));
    }
}
