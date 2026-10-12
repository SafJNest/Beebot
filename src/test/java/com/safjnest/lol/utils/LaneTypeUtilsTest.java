package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.R4B.lol.*;

public class LaneTypeUtilsTest {

    @Test
    public void exposesTheFivePlayableLanesInStableApiOrder() {
        assertEquals(List.of(LaneType.TOP, LaneType.JUNGLE, LaneType.MID, LaneType.BOT, LaneType.UTILITY), LaneType.playables());
        assertEquals("MIDDLE", LaneType.apiName(LaneType.MID));
        assertEquals("SUPPORT", LaneType.apiName(LaneType.UTILITY));
        assertEquals("AUTOFILL", LaneType.apiName(LaneType.NONE));
        assertEquals(5, LaneType.playableOrder(LaneType.NONE));
    }
}
