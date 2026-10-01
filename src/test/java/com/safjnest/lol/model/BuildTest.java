package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.champion.RuneSignature;

public class BuildTest {

    @Test
    public void persistsCurrentBuildThroughJson() {
        Build source = new Build(
            null,
            List.of(1),
            List.of(new Build.SlotOption(2, 10, 0.5)),
            List.of(),
            List.of(3),
            List.of(List.of(new Build.SlotOption(4, 8, 0.6))),
            List.of(),
            List.of(),
            List.of(),
            List.of(1, 2, 3),
            new RuneSignature(1, 2, List.of(3), 4, List.of(5), List.of(6, 7, 8)),
            10,
            0.5
        );

        String json = source.toJson();
        assertEquals('{', json.charAt(0));
        org.junit.Assert.assertFalse(json.contains("orderedCore"));
        org.junit.Assert.assertFalse(json.contains("orderedItems"));
        org.junit.Assert.assertFalse(json.contains("orderedAugments"));
        org.junit.Assert.assertFalse(json.contains("paths"));
        assertEquals(java.util.Set.of("filter", "games", "wins", "winrate", "coreBuilds", "coreItems",
            "starters", "boots", "supportItems", "roleBoundItems", "slots", "runes", "summonerSpells",
            "skillOrders", "prismatics", "augments"), new org.json.JSONObject(json).keySet());
        assertEquals(source, Build.fromJson(json));
        Build legacy = Build.fromJson(json.replace(",\"roleBoundItems\":[]", "")
            .replace(",\"timedOrder\":[]", ""));
        assertEquals(List.of(), legacy.roleBoundItemOptions());
        assertEquals(List.of(), legacy.skillOrders().get(0).timedOrder());
        assertNull(Build.fromJson("not-json"));
    }
}
