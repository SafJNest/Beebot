package com.safjnest.status;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StatusRatesTest {

    @Test
    public void convertsByteDeltasToPerSecondRates() {
        assertEquals(1_000, StatusRates.bytesPerSecond(0, 1_000, 1_000_000_000L));
        assertEquals(2_000, StatusRates.bytesPerSecond(100, 1_100, 500_000_000L));
        assertEquals(0, StatusRates.bytesPerSecond(50, 40, 1_000_000_000L));
        assertEquals(0, StatusRates.bytesPerSecond(0, 100, 0));
    }

    @Test
    public void convertsOpDeltasToPerSecondRates() {
        assertEquals(10.0, StatusRates.opsPerSecond(0, 10, 1_000_000_000L), 0.0001);
        assertEquals(20.0, StatusRates.opsPerSecond(100, 110, 500_000_000L), 0.0001);
        assertEquals(0.0, StatusRates.opsPerSecond(50, 40, 1_000_000_000L), 0.0001);
        assertEquals(0.0, StatusRates.opsPerSecond(0, 100, 0), 0.0001);
    }

    @Test
    public void convertsLoadToRoundedPercent() {
        assertEquals(13.7, StatusRates.percent(0.137), 0.0001);
        assertEquals(0.0, StatusRates.percent(-1), 0.0001);
        assertEquals(0.0, StatusRates.percent(Double.NaN), 0.0001);
        assertEquals(100.0, StatusRates.percent(1.5), 0.0001);
    }
}
