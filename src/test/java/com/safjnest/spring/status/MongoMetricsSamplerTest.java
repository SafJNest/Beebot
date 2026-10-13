package com.safjnest.spring.status;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import com.R4B.api.dto.status.MongoOperationRates;
import com.R4B.api.dto.status.MongoOperationSample;

public class MongoMetricsSamplerTest {

    @Test
    public void keepsSeriesAtConfiguredCapacity() throws Exception {
        Constructor<MongoMetricsSampler> constructor = MongoMetricsSampler.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        MongoMetricsSampler instance = constructor.newInstance();
        Field seriesField = MongoMetricsSampler.class.getDeclaredField("series");
        seriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MongoOperationSample> series = (List<MongoOperationSample>) seriesField.get(instance);
        Method appendSample = MongoMetricsSampler.class.getDeclaredMethod("appendSample", MongoOperationSample.class);
        appendSample.setAccessible(true);
        MongoOperationRates rates = new MongoOperationRates(0, 0, 0, 0, 0, 0, 0);

        for (int index = 0; index < MongoMetricsSampler.SERIES_CAPACITY + 5; index++) {
            appendSample.invoke(instance, new MongoOperationSample(index, rates));
        }

        assertEquals(MongoMetricsSampler.SERIES_CAPACITY, series.size());
        assertEquals(5, series.getFirst().at());
        assertEquals(MongoMetricsSampler.SERIES_CAPACITY + 4, series.getLast().at());
    }
}
