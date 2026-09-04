package com.safjnest.sql.database;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LeagueDBTest {

    @Test
    public void readsParticipantCountersFromLegacyKda() {
        assertEquals(175, LeagueDB.kdaValue("175/12/3", 0));
        assertEquals(12, LeagueDB.kdaValue("175/12/3", 1));
        assertEquals(3, LeagueDB.kdaValue("175/12/3", 2));
    }

    @Test
    public void rejectsMalformedLegacyKda() {
        assertEquals(0, LeagueDB.kdaValue("175/12", 0));
        assertEquals(0, LeagueDB.kdaValue("175/not-a-number/3", 1));
        assertEquals(0, LeagueDB.kdaValue(null, 0));
    }
}
