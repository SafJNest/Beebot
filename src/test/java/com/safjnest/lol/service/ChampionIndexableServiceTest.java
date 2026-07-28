package com.safjnest.lol.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChampionIndexableServiceTest {

    @Test
    public void shouldIndexRolesWithAtLeastTenPercentOfChampionGames() {
        assertTrue(ChampionIndexableService.isIndexable(60, 100));
        assertTrue(ChampionIndexableService.isIndexable(30, 100));
        assertTrue(ChampionIndexableService.isIndexable(10, 100));
        assertFalse(ChampionIndexableService.isIndexable(2, 100));
        assertFalse(ChampionIndexableService.isIndexable(0, 100));
        assertFalse(ChampionIndexableService.isIndexable(10, 0));
    }
}
