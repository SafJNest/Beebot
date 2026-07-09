package com.safjnest.spring.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class LolRegionParserTest {

    @Test
    public void shouldParseRegionAliases() {
        assertEquals(LeagueShard.EUW1, LolRegionParser.parse("euw"));
        assertEquals(LeagueShard.KR, LolRegionParser.parse("kr"));
        assertEquals(LeagueShard.NA1, LolRegionParser.parse("na"));
    }

    @Test(expected = ResponseStatusException.class)
    public void shouldRejectInvalidRegion() {
        LolRegionParser.parse("invalid");
    }
}
