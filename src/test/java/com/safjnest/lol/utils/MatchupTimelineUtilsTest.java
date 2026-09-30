package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MatchupTimelineUtilsTest {

    @Test
    public void resolvesSnapshotAndEarlyKillsByParticipantReferenceAtFifteenMinutes() {
        String events = """
            {
              "participants":{"1":"player","2":"opponent"},
              "snapshots":[
                {"timestamp":899999,"participants":{"1":{"total_gold":5000,"cs":100,"xp":5000,"level":10}}},
                {"timestamp":900000,"minute":15,"participants":{"1":{"total_gold":6000,"cs":120,"xp":7000,"level":12},"2":{"total_gold":5500,"cs":110,"xp":6500,"level":11}}}
              ],
              "champion_kills":[
                {"timestamp":900000,"killer":1},
                {"timestamp":900001,"killer":2}
              ],
              "turret_plate_events":[
                {"timestamp":900000,"killer":0,"team":"BLUE","lane":"BOT"},
                {"timestamp":900001,"team":"RED","lane":"BOT"}
              ]
            }
            """;

        MatchupTimelineUtils.Data data = MatchupTimelineUtils.read(events);

        assertEquals(Integer.valueOf(6000), data.snapshots().get("player").gold());
        assertEquals(Integer.valueOf(120), data.snapshots().get("player").cs());
        assertEquals(Integer.valueOf(7000), data.snapshots().get("player").xp());
        assertEquals(Integer.valueOf(12), data.snapshots().get("player").level());
        assertEquals(1, data.kills().get("player").intValue());
        assertFalse(data.kills().containsKey("opponent"));
        assertEquals(1, MatchupTimelineUtils.plates(data, "BLUE", "UTILITY"));
        assertEquals(0, MatchupTimelineUtils.plates(data, "RED", "BOT"));
    }

    @Test
    public void absentTimelineAndMissingSnapshotFieldsStayAbsent() {
        assertFalse(MatchupTimelineUtils.read((Object) null).killsAvailable());
        assertFalse(MatchupTimelineUtils.hasTimeline(null));
        assertFalse(MatchupTimelineUtils.hasTimeline("{}"));
        assertFalse(MatchupTimelineUtils.hasTimeline("{\"participants\":{}}"));
        assertFalse(MatchupTimelineUtils.hasTimeline("{\"participants\":{\"1\":\"player\"}}"));
        assertTrue(MatchupTimelineUtils.hasTimeline("{\"participants\":{\"1\":\"player\"},\"item_events\":[]}"));
        MatchupTimelineUtils.Data data = MatchupTimelineUtils.read("""
            {"participants":{"1":"player"},"snapshots":[
              {"timestamp":900000,"minute":15,"participants":{"1":{"cs":80}}}
            ]}
            """);

        assertTrue(data.snapshots().containsKey("player"));
        assertNull(data.snapshots().get("player").gold());
        assertNull(data.snapshots().get("player").xp());
        assertFalse(data.killsAvailable());
        assertEquals(0, MatchupTimelineUtils.plates(data, "BLUE", "TOP"));
    }

}
