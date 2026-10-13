package com.safjnest.spring.lol.model;
import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.safjnest.spring.lol.service.BuildCodec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

public class BuildTest {

    @Test
    public void persistsCurrentBuildThroughJson() {
        Build source = new Build(
            null,
            List.of(1),
            List.of(new Build.SlotOption(2, 10, 0.5)),
            List.of(),
            List.of(3),
            List.of(List.of(new Build.SlotOption(4, 8, 0.6))),
            List.of(),
            List.of(),
            List.of(),
            List.of(1, 2, 3),
            new RuneSignature(1, 2, List.of(3), 4, List.of(5), List.of(6, 7, 8)),
            10,
            0.5
        );

        String json = BuildCodec.toJson(source);
        assertEquals('{', json.charAt(0));
        assertEquals(source, BuildCodec.fromJson(json));
        Build legacy = BuildCodec.fromJson(json.replace(",\"roleBoundItems\":[]", "")
            .replace(",\"timedOrder\":[]", ""));
        assertEquals(List.of(), legacy.roleBoundItemOptions());
        assertEquals(List.of(), legacy.skillOrders().get(0).timedOrder());
        assertNull(BuildCodec.fromJson("not-json"));
    }
}
