package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class LaneTypeUtilsTest {

    @Test
    public void exposesTheFivePlayableLanesInStableApiOrder() {
        assertEquals(List.of(LaneType.TOP, LaneType.JUNGLE, LaneType.MID, LaneType.BOT, LaneType.UTILITY), LaneTypeUtils.playables());
        assertEquals("MIDDLE", LaneTypeUtils.apiName(LaneType.MID));
        assertEquals("SUPPORT", LaneTypeUtils.apiName(LaneType.UTILITY));
        assertEquals("AUTOFILL", LaneTypeUtils.apiName(LaneType.NONE));
        assertEquals(5, LaneTypeUtils.playableOrder(LaneType.NONE));
    }
}
