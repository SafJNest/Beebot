package com.safjnest.lol.champion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChampionBuildProviderTest {

    @Test
    public void providerBatchContractIsOneHundredRecords() {
        assertEquals(100, ChampionBuildProvider.BATCH_SIZE);
    }
}
