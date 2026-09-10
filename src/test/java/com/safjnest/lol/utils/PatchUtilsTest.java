package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PatchUtilsTest {

    private List<String> originalPatches;

    @Before
    public void savePatches() {
        originalPatches = new ArrayList<>(PatchUtils.getPatches());
    }

    @After
    public void restorePatches() {
        PatchUtils.getPatches().clear();
        PatchUtils.getPatches().addAll(originalPatches);
    }

    @Test
    public void returnsRecentPatchesInAscendingVersionOrder() {
        PatchUtils.getPatches().clear();
        PatchUtils.getPatches().addAll(List.of("16.17", "16.15", "16.16", "16.9", "16.10"));

        assertEquals(List.of("16.15", "16.16", "16.17"), PatchUtils.getRecentPatches(3));
    }
}
