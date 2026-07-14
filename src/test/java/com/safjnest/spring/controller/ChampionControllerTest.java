package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ChampionControllerTest {

    @Test
    public void shouldUseNullForUnfilteredRankAndRegion() {
        assertNull(LolApiParameters.rank(null));
        assertNull(LolApiParameters.region(null));
        assertEquals(TierType.DIAMOND, LolApiParameters.rank(" diamond "));
        assertEquals(LeagueShard.EUW1, LolApiParameters.region(" euw1 "));
    }

    @Test
    public void shouldUseSoloQueueAndParseRoleByDefault() {
        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO, LolApiParameters.queue(null));
        assertEquals(LaneType.UTILITY, LolApiParameters.role("utility"));
    }

    @Test
    public void shouldRejectInvalidChampionParameters() {
        assertEquals(HttpStatus.BAD_REQUEST, invalidRank("ALL").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidRegion("unknown").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidRegion("GLOBAL").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidRole("invalid").getStatusCode());
    }

    @Test
    public void shouldRejectRoleForQueueWithoutLanes() {
        try {
            new ChampionController().champion("thresh", null, null, "CHERRY", "UTILITY");
            throw new AssertionError("Expected role validation failure");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
    }

    private ResponseStatusException invalidRank(String value) {
        try {
            LolApiParameters.rank(value);
            throw new AssertionError("Expected invalid rank");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }

    private ResponseStatusException invalidRegion(String value) {
        try {
            LolApiParameters.region(value);
            throw new AssertionError("Expected invalid region");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }

    private ResponseStatusException invalidRole(String value) {
        try {
            LolApiParameters.role(value);
            throw new AssertionError("Expected invalid role");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }
}
