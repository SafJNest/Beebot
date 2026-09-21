package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.match.MatchOrder;
import com.safjnest.lol.model.match.RankHistoryQuery;
import com.safjnest.lol.model.match.RankHistoryView;
import com.safjnest.lol.utils.SeasonUtils;

public class RankHistoryParametersTest {

    @Test
    public void shouldResolveSeasonYears() {
        assertEquals(15, SeasonUtils.getSeasonRange(2025).season());
        assertEquals(2025, SeasonUtils.getSeasonRange(2025).year());
        assertEquals(16, SeasonUtils.getSeasonRange(2026).season());
    }

    @Test
    public void shouldParseRankHistoryModes() {
        RankHistoryQuery season = LolApiParameters.rankHistoryQuery(
            null, null, 2025, null, 1760000000000L, 0, "timeStart:asc");
        RankHistoryQuery profile = LolApiParameters.rankHistoryQuery(
            null, "profile", null, null, 0, 0, null);
        RankHistoryQuery patch = LolApiParameters.rankHistoryQuery(
            null, null, null, "14.10", 0, 0, null);

        assertEquals(Integer.valueOf(2025), season.season());
        assertEquals(1760000000000L, season.timeStart());
        assertEquals(MatchOrder.ASC, season.order());
        assertEquals(RankHistoryView.PROFILE, profile.view());
        assertEquals("14.10", patch.patch());
    }

    @Test
    public void shouldRejectIncompatibleRankHistoryModes() {
        assertInvalid(null, "profile", 2025, null, 0, 0);
        assertInvalid(null, null, 2025, "15.1", 0, 0);
        assertInvalid(null, null, null, "15.1", 1, 0);
        assertInvalid(null, null, null, null, 1, 2);
        assertInvalid(null, null, 2035, null, 0, 0);
    }

    private void assertInvalid(
        String queue,
        String view,
        Integer season,
        String patch,
        long timeStart,
        long timeEnd
    ) {
        try {
            LolApiParameters.rankHistoryQuery(queue, view, season, patch, timeStart, timeEnd, null);
            throw new AssertionError("Expected invalid rank history query");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
            assertTrue(exception.getReason().contains("Invalid"));
        }
    }
}
