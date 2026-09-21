package com.safjnest.status;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.status.MongoMetrics;
import com.safjnest.lol.model.status.MongoOperationSample;

public class MongoMetricsSamplerTest {

    @Test
    public void keepsSeriesAtConfiguredCapacity() throws Exception {
        Field instanceField = MongoMetricsSampler.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);
        Field seriesField = MongoMetricsSampler.class.getDeclaredField("series");
        seriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MongoOperationSample> series = (List<MongoOperationSample>) seriesField.get(instance);
        series.clear();

        for (int index = 0; index < MongoMetricsSampler.SERIES_CAPACITY + 5; index++) {
            MongoMetricsSampler.sampleTick();
        }

        MongoMetrics snapshot = MongoMetricsSampler.snapshot();
        assertEquals(MongoMetricsSampler.SERIES_CAPACITY, snapshot.operations().series().size());
    }
}
