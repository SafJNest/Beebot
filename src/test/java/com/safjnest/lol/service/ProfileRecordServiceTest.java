package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ProfileRecordServiceTest {

    @Test
    public void competitionRankingDoesNotSplitEqualScores() {
        assertEquals(Long.valueOf(1), ProfileRecordService.ranking(0L, 0L));
        assertEquals(Long.valueOf(1), ProfileRecordService.ranking(1L, 0L));
        assertEquals(Long.valueOf(3), ProfileRecordService.ranking(2L, 2L));
        assertNull(ProfileRecordService.ranking(null, 2L));
    }
}
