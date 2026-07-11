package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class LolControllerTest {

    @Test
    public void shouldParseEveryOperationalR4jLeagueShard() {
        for (LeagueShard shard : LeagueShard.values()) {
            if (shard == LeagueShard.UNKNOWN) continue;
            assertEquals(shard, LolController.parseShard(" " + shard.name().toLowerCase() + " "));
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

    private ResponseStatusException invalidShard(String value) {
        try {
            LolController.parseShard(value);
            throw new AssertionError("Expected an invalid LeagueShard");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }
}
