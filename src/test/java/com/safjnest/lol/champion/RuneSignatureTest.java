package com.safjnest.lol.champion;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.utils.BuildUtils;

public class RuneSignatureTest {

    @Test
    public void keepsRuneKeyOrderAndGroupsStatShardsOutsideTheKey() {
        RuneSignature signature = new RuneSignature(
            8000, 8005, List.of(9101, 9111),
            8100, List.of(8139, 8126), List.of(5008, 5002, 5003)
        );

        assertEquals("8000|8005|9101-9111|8100|8139-8126", BuildUtils.fromBase64(signature.toKey()));
        assertEquals("5008-5002-5003", signature.statShardsKey());
        assertEquals(new RuneSignature(8000, 8005, List.of(9101, 9111), 8100,
            List.of(8139, 8126), List.of()), RuneSignature.decode(signature.toKey()));
    }
}
