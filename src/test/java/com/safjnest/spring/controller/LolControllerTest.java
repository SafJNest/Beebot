package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class LolControllerTest {

    @Test
    public void shouldParseEveryOperationalR4jLeagueShard() {
        for (LeagueShard shard : LeagueShard.values()) {
            if (shard == LeagueShard.UNKNOWN) continue;
            assertEquals(shard, LolApiParameters.requiredShard(" " + shard.name().toLowerCase() + " "));
        }
    }

    @Test
    public void shouldRejectR4jAliases() {
        ResponseStatusException exception = invalidShard("euw");
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("EUW1"));
    }

    @Test
    public void shouldRejectUnknownShard() {
        assertEquals(HttpStatus.BAD_REQUEST, invalidShard("unknown").getStatusCode());
    }

    @Test
    public void shouldParseProfileMatchupsFilterDefaults() {
        com.safjnest.lol.model.ActivityFilter filter = LolApiParameters.matchupsFilter(0, 0, null, null, null, 5);

        assertNull(filter.queue());
        assertNull(filter.patch());
        assertNull(filter.lane());
        assertEquals(5, filter.minGames());
    }

    @Test
    public void shouldParseProfileMatchupsFilterValues() {
        com.safjnest.lol.model.ActivityFilter filter = LolApiParameters.matchupsFilter(
            0, 0, "ranked_flex_sr", "14.10", "top", 8);

        assertEquals(GameQueueType.RANKED_FLEX_SR, filter.queue());
        assertEquals("14.10", filter.patch());
        assertEquals(LaneType.TOP, filter.lane());
        assertEquals(8, filter.minGames());
    }

    @Test
    public void shouldPreferStartAndEndOverPatch() {
        com.safjnest.lol.model.ActivityFilter filter = LolApiParameters.matchupsFilter(
            1711929600000L, 1714521600000L, null, "14.10", null, 5);

        assertEquals(1711929600000L, filter.timeStart());
        assertEquals(1714521600000L, filter.timeEnd());
        assertNull(filter.patch());
    }

    @Test
    public void shouldRejectInvalidProfileMatchupsParameters() {
        try {
            LolApiParameters.matchupsFilter(0, 0, null, "14", null, 5);
            throw new AssertionError("Expected invalid patch");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        try {
            LolApiParameters.matchupsFilter(0, 0, "aram", null, "top", 5);
            throw new AssertionError("Expected invalid role");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        try {
            LolApiParameters.matchupsFilter(0, 0, null, null, null, 0);
            throw new AssertionError("Expected invalid minimum games");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        try {
            LolApiParameters.matchupsFilter(1711929600000L, 0, null, null, null, 5);
            throw new AssertionError("Expected incomplete period");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
    }

    @Test
    public void shouldMapProfileMatchupsResponseStates() {
        ProfileMatchups payload = new ProfileMatchups(null, 0, 0, 0, java.util.List.of());
        ResponseEntity<?> ready = LolApiResponses.from(
            ApiResult.ready(payload), "pending", "Pending", "Not found");
        ResponseEntity<?> pending = LolApiResponses.from(
            ApiResult.pending(), "profile_matchups_pending", "Pending", "Not found");

        assertEquals(HttpStatus.OK, ready.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, pending.getStatusCode());
        try {
            LolApiResponses.from(ApiResult.notFound(), "pending", "Pending", "Not found");
            throw new AssertionError("Expected not found");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }
    }

    private ResponseStatusException invalidShard(String value) {
        try {
            LolApiParameters.requiredShard(value);
            throw new AssertionError("Expected an invalid LeagueShard");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }
}
