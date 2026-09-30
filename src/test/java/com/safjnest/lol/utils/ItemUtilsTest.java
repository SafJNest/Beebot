package com.safjnest.lol.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ItemUtilsTest {

    @Test
    public void identifiesPrismaticItemsFromIdWithoutStaticItemMetadata() {
        assertFalse(ItemUtils.isPrismatic(440000));
        assertTrue(ItemUtils.isPrismatic(440001));
        assertTrue(ItemUtils.isPrismatic(444444));
        assertFalse(ItemUtils.isPrismatic(44444));
    }
}
