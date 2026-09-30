package com.safjnest.lol.champion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.json.JSONArray;
import org.junit.Test;

import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.utils.ChampionBuildTimelineUtils;

public class BuildSignatureTest {

    @Test
    public void ordersFinalInventoryByAttributedTimelineAndKeepsUnmatchedItems() {
        List<Integer> inventory = List.of(3078, 3031, 3031, 3006);
        List<ChampionBuildTimelineUtils.ItemEvent> events = List.of(
            new ChampionBuildTimelineUtils.ItemEvent(3031, 100),
            new ChampionBuildTimelineUtils.ItemEvent(3006, 200),
            new ChampionBuildTimelineUtils.ItemEvent(9999, 300),
            new ChampionBuildTimelineUtils.ItemEvent(3031, 400)
        );

        assertEquals(List.of(3031, 3006, 3031, 3078), BuildSignature.orderByTimeline(inventory, events));
    }

    @Test
    public void timelineSkillOrderTakesPriorityAndUsesUpgradeSequenceWithoutLevels() {
        List<ChampionBuildTimelineUtils.SkillUpgrade> upgrades = List.of(
            new ChampionBuildTimelineUtils.SkillUpgrade(null, 2, 100),
            new ChampionBuildTimelineUtils.SkillUpgrade(null, 1, 200),
            new ChampionBuildTimelineUtils.SkillUpgrade(3, 3, 300)
        );

        List<Integer> order = BuildSignature.skillOrder(new JSONArray("[1,1,3]"), upgrades);

        assertEquals(18, order.size());
        assertEquals(List.of(2, 1, 3), order.subList(0, 3));
        assertEquals(0, order.get(3).intValue());
    }

    @Test
    public void participantSkillOrderRemainsFallbackWhenTimelineHasNoSkillEvents() {
        List<Integer> order = BuildSignature.skillOrder(new JSONArray("[1,3,2]"), List.of());

        assertEquals(List.of(1, 3, 2), order.subList(0, 3));
        assertEquals(18, order.size());
    }

    @Test
    public void keepsFinalInventoryItemsWhenStaticItemMetadataIsMissing() {
        assertFalse(BuildSignature.isSkippable(999999, null));
        assertTrue(BuildSignature.isSkippable(0, null));
        assertTrue(BuildSignature.isSkippable(2003, null));
    }

    @Test
    public void keepsBuildKeyFieldOrderAndRepeatedItemIds() {
        BuildSignature signature = new BuildSignature(
            List.of(3340), 3006, 3865, List.of(3078, 3031),
            List.of(3078, 3031, 3031), List.of(1, 2), List.of(440001),
            List.of(9001), List.of(4, 14)
        );

        assertEquals("3340|3006|3865|3078-3031|3078-3031-3031|1-2|440001|9001|4-14",
            BuildUtils.fromBase64(signature.toKey()));
        BuildSignature decoded = BuildSignature.decode(signature.toKey());
        assertEquals(signature.starter(), decoded.starter());
        assertEquals(signature.core(), decoded.core());
        assertEquals(signature.fullBuild(), decoded.fullBuild());
        assertEquals(signature.spellOrder(), decoded.spellOrder().subList(0, 2));
        assertEquals(18, decoded.spellOrder().size());
        assertEquals("3340|3865|3078-3031", BuildUtils.fromBase64(signature.toCoreKey()));
    }

    @Test
    public void decodesLegacyUnseparatedSkillOrderWithoutChangingKeyFields() {
        String oldKey = BuildUtils.toBase64("3340|3006|3865|3078-3031|3078|123412341234123412|440001|9001|4-14");

        BuildSignature decoded = BuildSignature.decode(oldKey);

        assertEquals(List.of(1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2), decoded.spellOrder());
        assertEquals("3340|3006|3865|3078-3031|3078|1-2-3-4-1-2-3-4-1-2-3-4-1-2-3-4-1-2|440001|9001|4-14",
            BuildUtils.fromBase64(decoded.toKey()));
    }
}
