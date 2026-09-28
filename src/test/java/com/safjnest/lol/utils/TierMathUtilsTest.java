package com.safjnest.lol.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class TierMathUtilsTest {

    @Test
    public void keepsTierListThresholdsIncludingD() {
        assertEquals("S+", TierMathUtils.tier(2));
        assertEquals("S", TierMathUtils.tier(1));
        assertEquals("A", TierMathUtils.tier(0.25));
        assertEquals("B", TierMathUtils.tier(-0.25));
        assertEquals("C", TierMathUtils.tier(-1));
        assertEquals("D", TierMathUtils.tier(-1.0001));
    }

    @Test
    public void medianWorksForOddAndEvenSamples() {
        assertEquals(5d, TierMathUtils.median(List.of(9, 1, 5)), 0d);
        assertEquals(4d, TierMathUtils.median(List.of(2, 6, 3, 5)), 0d);
    }

    @Test
    public void shrinkageMovesSmallSamplesTowardThePrior() {
        assertEquals(0.6, TierMathUtils.shrinkRate(7, 10, 0.5, 10), 0.000001);
        assertEquals(75d, TierMathUtils.shrinkValue(100d, 10, 50d, 10), 0.000001);
    }

    @Test
    public void zeroVarianceProducesDeterministicZeroZScore() {
        TierMathUtils.Moments moments = TierMathUtils.moments(List.of(1d, 1d, 1d));
        assertEquals(0d, TierMathUtils.z(1d, moments), 0d);
    }
}
