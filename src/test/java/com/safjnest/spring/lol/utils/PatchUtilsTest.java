package com.safjnest.spring.lol.utils;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

public class PatchUtilsTest {

    @Test
    public void comparesPatchVersionsNumerically() throws Exception {
        Method compare = PatchUtils.class.getDeclaredMethod("comparePatches", String.class, String.class);
        compare.setAccessible(true);
        assertEquals(1, Integer.signum((int) compare.invoke(null, "16.10", "16.9")));
        assertEquals(-1, Integer.signum((int) compare.invoke(null, "16.15", "16.17")));
        assertEquals(0, compare.invoke(null, "16.16", "16.16"));
    }

    @Test
    public void returnsRecentPatchesInAscendingVersionOrderWithoutMutatingTheCatalog() {
        List<String> catalog = new ArrayList<>(PatchUtils.getPatches());
        List<String> recent = PatchUtils.getRecentPatches(3);
        assertEquals(Math.min(3, catalog.size()), recent.size());
        assertEquals(catalog, PatchUtils.getPatches());
        for (int index = 1; index < recent.size(); index++) {
            String[] previous = recent.get(index - 1).split("\\.");
            String[] current = recent.get(index).split("\\.");
            org.junit.Assert.assertTrue(Integer.parseInt(previous[0]) < Integer.parseInt(current[0])
                || Integer.parseInt(previous[0]) == Integer.parseInt(current[0])
                && Integer.parseInt(previous[1]) <= Integer.parseInt(current[1]));
        }
    }
}
