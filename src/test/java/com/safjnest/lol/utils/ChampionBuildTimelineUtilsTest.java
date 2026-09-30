package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class ChampionBuildTimelineUtilsTest {

    @Test
    public void extractsTimedSkillsByPuuidAndUsesLevelEvents() {
        String events = """
            {"participants":"{\\"1\\":\\"player\\",\\"2\\":\\"other\\"}",
             "skill_events":"[{\\"timestamp\\":125000,\\"participant\\":1,\\"skill_slot\\":2},{\\"timestamp\\":126000,\\"participant\\":0,\\"skill_slot\\":3},{\\"timestamp\\":127000,\\"participant\\":2,\\"skill_slot\\":1}]",
             "level_events":"[{\\"timestamp\\":120000,\\"participant\\":1,\\"level\\":2}]"}
            """;

        List<ChampionBuildTimelineUtils.SkillUpgrade> upgrades = ChampionBuildTimelineUtils.skillUpgrades(events, "player");

        assertEquals(1, upgrades.size());
        assertEquals(Integer.valueOf(2), upgrades.get(0).championLevel());
        assertEquals(2, upgrades.get(0).skillSlot());
        assertEquals(125000, upgrades.get(0).timestampMillis());
    }

    @Test
    public void itemEventsUndoSalesAndIgnoreUnattributedEvents() {
        String events = """
            {"participants":{"1":"player"},"item_events":[
              {"timestamp":1000,"event":"ITEM_PURCHASED","participant":1,"item":3006},
              {"timestamp":2000,"event":"ITEM_SOLD","participant":1,"item":3006,"before":3006},
              {"timestamp":3000,"event":"ITEM_UNDO","participant":1,"item":0,"before":3006},
              {"timestamp":4000,"event":"ITEM_PURCHASED","participant":1,"item":3000},
              {"timestamp":5000,"event":"ITEM_PURCHASED","participant":1,"item":3123,"before":3000,"after":3123},
              {"timestamp":6000,"event":"ITEM_PURCHASED","participant":1,"item":3200},
              {"timestamp":7000,"event":"ITEM_UNDO","participant":1,"item":0,"after":3200},
              {"timestamp":8000,"event":"ITEM_PURCHASED","participant":1,"item":4000},
              {"timestamp":9000,"event":"ITEM_SOLD","participant":1,"item":4000,"before":4000},
              {"timestamp":10000,"event":"ITEM_PURCHASED","participant":0,"item":3083}
            ]}
            """;

        List<ChampionBuildTimelineUtils.ItemEvent> items = ChampionBuildTimelineUtils.itemEvents(events, "player");

        assertEquals(List.of(new ChampionBuildTimelineUtils.ItemEvent(3006, 1000),
            new ChampionBuildTimelineUtils.ItemEvent(3123, 5000)), items);
    }

    @Test
    public void itemEventsKeepRepeatedAcquisitionsOfTheSameItem() {
        String events = """
            {"participants":{"1":"player"},"item_events":[
              {"timestamp":1000,"event":"ITEM_PURCHASED","participant":1,"item":2003},
              {"timestamp":2000,"event":"ITEM_PURCHASED","participant":1,"item":2003}
            ]}
            """;

        assertEquals(List.of(new ChampionBuildTimelineUtils.ItemEvent(2003, 1000),
                new ChampionBuildTimelineUtils.ItemEvent(2003, 2000)),
            ChampionBuildTimelineUtils.itemEvents(events, "player"));
    }

    @Test
    public void starterEventsUseOnlyTheAttributedOpeningWindowAndUndoState() {
        String events = """
            {"participants":{"1":"player"},"item_events":[
              {"timestamp":1000,"event":"ITEM_PURCHASED","participant":1,"item":1055},
              {"timestamp":2000,"event":"ITEM_PURCHASED","participant":1,"item":2003},
              {"timestamp":3000,"event":"ITEM_UNDO","participant":1,"item":0,"after":2003},
              {"timestamp":119999,"event":"ITEM_PURCHASED","participant":1,"item":3078},
              {"timestamp":120000,"event":"ITEM_PURCHASED","participant":1,"item":3031},
              {"timestamp":1100,"event":"ITEM_PURCHASED","participant":0,"item":3083}
            ]}
            """;

        assertEquals(List.of(new ChampionBuildTimelineUtils.ItemEvent(1055, 1000),
                new ChampionBuildTimelineUtils.ItemEvent(3078, 119999)),
            ChampionBuildTimelineUtils.starterItemEvents(events, "player"));
    }
}
