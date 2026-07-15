package com.safjnest.utils;

import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;

public class KryoUtilsTest {

    @Test
    public void invalidPayloadReturnsNull() {
        assertNull(KryoUtils.decode("not-a-kryo-payload", ProfileStatistics.class));
    }

    @Test
    public void incompatiblePayloadReturnsNull() {
        String encoded = KryoUtils.encode(new Stats<>());
        assertNull(KryoUtils.decode(encoded, ProfileStatistics.class));
    }
}
