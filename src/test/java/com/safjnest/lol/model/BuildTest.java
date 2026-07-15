package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.build.RuneSignature;

public class BuildTest {

    @Test
    public void persistsCurrentBuildThroughKryo() {
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

        assertEquals(source, Build.decode(source.encode()));
    }
}
