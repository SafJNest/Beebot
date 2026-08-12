package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;

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
            new ChampionController().champion("thresh", null, null, null, null, "utility");
            throw new AssertionError("Expected role validation failure");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
    }

    @Test
    public void shouldExposeIndexablesEndpointWithoutParameters() throws Exception {
        java.lang.reflect.Method method = ChampionController.class.getDeclaredMethod("indexables");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertEquals("/champion/indexables", mapping.value()[0]);
        assertEquals(0, method.getParameterCount());
    }

    @Test
    public void shouldExposeTierListEndpointWithSharedChampionFilters() throws Exception {
        java.lang.reflect.Method method = ChampionController.class.getDeclaredMethod("tierList",
            String.class, String.class, String.class, String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertEquals("/champions/tier-list", mapping.value()[0]);
        assertEquals(4, method.getParameterCount());
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
