package com.safjnest.lol.service;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class StaticDataServicePatchTest {
    @Test
    public void requestedMinorUsesItsOwnFullVersionInsteadOfTheCurrentPatch() {
        assertEquals("16.19.2", StaticDataService.resolveItemVersion("16.19",
            List.of("16.20.1", "16.19.2", "16.19.1")));
        assertEquals("16.19.1", StaticDataService.resolveItemVersion("16.19.1", List.of("16.20.1")));
        assertThrows(IllegalArgumentException.class,
            () -> StaticDataService.resolveItemVersion("16.17", List.of("16.20.1", "16.19.2")));
    }
}
