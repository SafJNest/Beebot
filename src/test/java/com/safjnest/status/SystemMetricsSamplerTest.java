package com.safjnest.status;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.R4B.api.dto.status.SystemMetrics;
import com.R4B.api.dto.status.JvmMetrics;
import oshi.SystemInfo;

public class SystemMetricsSamplerTest {

    private volatile boolean running;
    private SystemMetricsSampler sampler;
    private Method sampleSystem;
    private Method sampleProcess;

    @Before
    public void startSampler() throws Exception {
        running = true;
        Thread load = new Thread(() -> {
            while (running) {
                Math.sqrt(running ? 42.0 : 0.0);
            }
        }, "cpu-load");
        load.setDaemon(true);
        load.start();
        Constructor<SystemMetricsSampler> constructor = SystemMetricsSampler.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        sampler = constructor.newInstance();
        SystemInfo info = new SystemInfo();
        set("systemInfo", info);
        set("processor", info.getHardware().getProcessor());
        set("operatingSystem", info.getOperatingSystem());
        sampleSystem = SystemMetricsSampler.class.getDeclaredMethod("sampleSystem", SystemMetrics.class);
        sampleProcess = SystemMetricsSampler.class.getDeclaredMethod("sampleProcess");
        sampleSystem.setAccessible(true);
        sampleProcess.setAccessible(true);
        sampleSystem.invoke(sampler, new Object[]{null});
        sampleProcess.invoke(sampler);
        Thread.sleep(2500);
    }

    @After
    public void stopSampler() {
        running = false;
    }

    @Test
    public void reportsNonZeroCpuAfterWarmup() throws Exception {
        SystemMetrics system = (SystemMetrics) sampleSystem.invoke(sampler, new Object[]{null});
        JvmMetrics jvm = (JvmMetrics) sampleProcess.invoke(sampler);
        assertNotNull(system);
        SystemMetrics.Cpu cpu = system.cpu();
        assertNotNull(cpu);
        assertNotNull(cpu.usage());
        assertTrue("system cpu usage should be > 0 after warmup", cpu.usage() > 0);
        assertNotNull(cpu.perCore());
        assertTrue("per-core cpu should be populated", !cpu.perCore().isEmpty());
        boolean anyCore = false;
        for (Double load : cpu.perCore()) {
            if (load != null && load > 0) {
                anyCore = true;
                break;
            }
        }
        assertTrue("at least one core should report usage", anyCore);
        assertNotNull(jvm.cpu());
        assertTrue("process cpu should be > 0 under load", jvm.cpu() > 0);
    }

    private void set(String name, Object value) throws Exception {
        Field field = SystemMetricsSampler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(sampler, value);
    }
}
