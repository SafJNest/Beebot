package com.safjnest.spring.lol.champion;
import com.R4B.api.dto.RuneSignature;
import com.R4B.lol.BuildValues;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class RuneSignatureTest {

    @Test
    public void keepsRuneKeyOrderAndGroupsStatShardsOutsideTheKey() {
        RuneSignature signature = new RuneSignature(
            8000, 8005, List.of(9101, 9111),
            8100, List.of(8139, 8126), List.of(5008, 5002, 5003)
        );

        assertEquals("8000|8005|9101-9111|8100|8139-8126", BuildValues.fromBase64(signature.toKey()));
        assertEquals("5008-5002-5003", signature.statShardsKey());
        assertEquals(new RuneSignature(8000, 8005, List.of(9101, 9111), 8100,
            List.of(8139, 8126), List.of()), RuneSignature.decode(signature.toKey()));
    }
}
