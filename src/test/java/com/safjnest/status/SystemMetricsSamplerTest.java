package com.safjnest.status;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.safjnest.lol.model.status.SystemMetrics;

public class SystemMetricsSamplerTest {

    private volatile boolean running;

    @Before
    public void startSampler() throws InterruptedException {
        running = true;
        Thread load = new Thread(() -> {
            while (running) {
                Math.sqrt(running ? 42.0 : 0.0);
            }
        }, "cpu-load");
        load.setDaemon(true);
        load.start();
        SystemMetricsSampler.start();
        Thread.sleep(2500);
    }

    @After
    public void stopSampler() {
        running = false;
        SystemMetricsSampler.stop();
    }

    @Test
    public void reportsNonZeroCpuAfterWarmup() {
        SampledMetrics metrics = SystemMetricsSampler.snapshot();
        SystemMetrics system = metrics.system();
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
        assertNotNull(metrics.jvm().cpu());
        assertTrue("process cpu should be > 0 under load", metrics.jvm().cpu() > 0);
    }
}
