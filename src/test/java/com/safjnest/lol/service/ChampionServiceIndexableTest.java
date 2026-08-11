package com.safjnest.lol.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChampionServiceIndexableTest {

    @Test
    public void shouldIndexRolesWithAtLeastTenPercentOfChampionGames() {
        assertTrue(ChampionService.isIndexable(60, 100));
        assertTrue(ChampionService.isIndexable(30, 100));
        assertTrue(ChampionService.isIndexable(10, 100));
        assertFalse(ChampionService.isIndexable(2, 100));
        assertFalse(ChampionService.isIndexable(0, 100));
        assertFalse(ChampionService.isIndexable(10, 0));
    }
}
