package com.safjnest.lol.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.safjnest.utils.JsonCodec;

public class ProfileIndexableTest {

    @Test
    public void shouldExposeOnlyUrlFields() {
        String json = JsonCodec.toJson(new ProfileIndexable("Player#EUW", "EUW1"));
        assertTrue(json.contains("\"riotId\":\"Player#EUW\""));
        assertTrue(json.contains("\"region\":\"EUW1\""));
        assertFalse(json.contains("puuid"));
        assertFalse(json.contains("lastUpdate"));
    }
}
