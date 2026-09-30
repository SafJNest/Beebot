package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.json.JSONArray;
import org.junit.Test;

public class BuildUtilsTest {

    @Test
    public void keepsStrictAndTolerantJsonArrayParsersDistinct() {
        JSONArray values = new JSONArray("[1,\"2\",\"bad\"]");

        assertEquals(List.of(1, 2, 0), BuildUtils.jsonArrayToIntList(values));
        assertThrows(RuntimeException.class, () -> BuildUtils.toIntList(values));
    }

    @Test
    public void keepsDashSeparatedKeyEncoding() {
        assertEquals(List.of(1, 20, 300), BuildUtils.parseDashList("1-20-300"));
        assertEquals("1-20-300", BuildUtils.joinInts(List.of(1, 20, 300)));
    }
}
