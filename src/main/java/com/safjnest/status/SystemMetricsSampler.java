package com.safjnest.status;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.safjnest.lol.model.status.JvmMetrics;
import com.safjnest.lol.model.status.MongoMetrics;
import com.safjnest.lol.model.status.RedisMetrics;
import com.safjnest.lol.model.status.SystemMetrics;
import com.safjnest.redis.RedisClient;
import com.safjnest.utils.log.BotLogger;

import com.sun.management.OperatingSystemMXBean;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public final class SystemMetricsSampler {

    private static final long SAMPLE_INTERVAL_SECONDS = 1;
    private static final int LEAGUE_METRICS_REFRESH_TICKS = 10;

    private static final SystemMetricsSampler INSTANCE = new SystemMetricsSampler();

    private final Object lock = new Object();
    private ScheduledExecutorService executor;
    private SystemInfo systemInfo;
    private CentralProcessor processor;
    private OperatingSystem operatingSystem;
    private long[] systemCpuTicks;
    private long[][] processorCpuTicks;
    private OSProcess previousProcess;
    private long previousNetworkAt;
    private long previousBytesReceived;
    private long previousBytesSent;
    private int flushTicks;
    private volatile SampledMetrics snapshot = SampledMetrics.empty();

    private SystemMetricsSampler() {}

    public static void start() {
        INSTANCE.startSampler();
    }

    public static void stop() {
        INSTANCE.stopSampler();
    }

    public static SampledMetrics snapshot() {
        return INSTANCE.snapshot;
    }

    // ============================================================================

    private void startSampler() {
        synchronized (lock) {
            if (executor != null) return;
            try {
                systemInfo = new SystemInfo();
                HardwareAbstractionLayer hardware = systemInfo.getHardware();
                processor = hardware.getProcessor();
                operatingSystem = systemInfo.getOperatingSystem();
            } catch (Exception exception) {
                BotLogger.error("Failed to initialize system metrics sampler: " + exception.getMessage());
            }
            LeagueMetricsStore.seed();
            sample();
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "system-metrics-sampler");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleAtFixedRate(this::safeSample, SAMPLE_INTERVAL_SECONDS, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void stopSampler() {
        ScheduledExecutorService current;
        synchronized (lock) {
            current = executor;
            executor = null;
        }
        LeagueMetricsStore.refresh();
        if (current == null) return;
        current.shutdownNow();
    }

    private void safeSample() {
        try {
            sample();
        } catch (Exception exception) {
            BotLogger.error("System metrics sample failed: " + exception.getMessage());
        }
    }

    private void sample() {
        JvmMetrics jvm = snapshot.jvm();
        SystemMetrics system = snapshot.system();
        RedisMetrics redis = snapshot.redis();
        MongoMetrics mongo = snapshot.mongo();

        try {
            jvm = sampleProcess();
        } catch (Exception ignored) {}

        try {
            system = sampleSystem(system);
        } catch (Exception ignored) {}

        try {
            RedisMetrics sampledRedis = sampleRedis();
            if (sampledRedis != null) redis = sampledRedis;
        } catch (Exception ignored) {}

        try {
            MongoMetricsSampler.sampleTick();
            mongo = MongoMetricsSampler.snapshot();
        } catch (Exception ignored) {}

        snapshot = new SampledMetrics(jvm, system, redis, mongo);

        flushTicks++;
        if (flushTicks >= LEAGUE_METRICS_REFRESH_TICKS) {
            flushTicks = 0;
            try {
                LeagueMetricsStore.refresh();
            } catch (Exception ignored) {}
        }
    }

    private JvmMetrics sampleProcess() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        return new JvmMetrics(
            processCpu(),
            new JvmMetrics.Memory(heap.getUsed(), heap.getCommitted(), heap.getMax()),
            threads.getThreadCount(),
            threads.getPeakThreadCount(),
            ManagementFactory.getRuntimeMXBean().getUptime()
        );
    }

    private Double processCpu() {
        Double mxBeanLoad = mxProcessCpu();
        if (operatingSystem == null) return mxBeanLoad;
        OSProcess current = operatingSystem.getProcess(operatingSystem.getProcessId());
        if (current == null) return mxBeanLoad;
        OSProcess previous = previousProcess;
        previousProcess = current;
        if (previous == null) return mxBeanLoad;
        double load = current.getProcessCpuLoadBetweenTicks(previous);
        if (load < 0 && mxBeanLoad != null) return mxBeanLoad;
        return StatusRates.percent(load);
    }

    private SystemMetrics sampleSystem(SystemMetrics previous) {
        SystemMetrics.Cpu cpu = previous == null ? null : previous.cpu();
        SystemMetrics.Memory memory = previous == null ? null : previous.memory();
        SystemMetrics.Disk disk = previous == null ? null : previous.disk();
        SystemMetrics.Network network = previous == null ? null : previous.network();

        try {
            cpu = sampleCpu(cpu);
        } catch (Exception ignored) {}
        try {
            memory = sampleMemory();
        } catch (Exception ignored) {}
        try {
            disk = sampleDisk();
        } catch (Exception ignored) {}
        try {
            SystemMetrics.Network sampledNetwork = sampleNetwork();
            if (sampledNetwork != null) network = sampledNetwork;
        } catch (Exception ignored) {}

        return new SystemMetrics(cpu, memory, disk, network);
    }

    private SystemMetrics.Cpu sampleCpu(SystemMetrics.Cpu previous) {
        if (processor == null) return previous;
        int cores = processor.getLogicalProcessorCount();
        if (systemCpuTicks == null || processorCpuTicks == null) {
            systemCpuTicks = copy(systemCpuTicks(processor));
            processorCpuTicks = copy(processorCpuTicks(processor));
            return new SystemMetrics.Cpu(null, cores, null);
        }
        double usage = StatusRates.percent(processor.getSystemCpuLoadBetweenTicks(systemCpuTicks));
        double[] loads = processor.getProcessorCpuLoadBetweenTicks(processorCpuTicks);
        List<Double> perCore = new ArrayList<>(loads.length);
        for (double load : loads) perCore.add(StatusRates.percent(load));
        if (usage <= 0) {
            boolean allZero = true;
            for (double load : loads) {
                if (StatusRates.percent(load) > 0) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                Double mxLoad = mxSystemCpu();
                if (mxLoad != null) usage = mxLoad;
            }
        }
        systemCpuTicks = copy(systemCpuTicks(processor));
        processorCpuTicks = copy(processorCpuTicks(processor));
        return new SystemMetrics.Cpu(usage, cores, List.copyOf(perCore));
    }

    private static long[] systemCpuTicks(CentralProcessor processor) {
        return processor.getSystemCpuLoadTicks();
    }

    private static long[][] processorCpuTicks(CentralProcessor processor) {
        return processor.getProcessorCpuLoadTicks();
    }

    private static long[] copy(long[] ticks) {
        return ticks == null ? null : ticks.clone();
    }

    private static long[][] copy(long[][] ticks) {
        if (ticks == null) return null;
        long[][] copy = new long[ticks.length][];
        for (int index = 0; index < ticks.length; index++) {
            copy[index] = ticks[index] == null ? null : ticks[index].clone();
        }
        return copy;
    }

    private static Double mxSystemCpu() {
        OperatingSystemMXBean bean = mxBean();
        if (bean == null) return null;
        double load = bean.getCpuLoad();
        return load < 0 ? null : StatusRates.percent(load);
    }

    private static Double mxProcessCpu() {
        OperatingSystemMXBean bean = mxBean();
        if (bean == null) return null;
        double load = bean.getProcessCpuLoad();
        return load < 0 ? null : StatusRates.percent(load);
    }

    private static OperatingSystemMXBean mxBean() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) return bean;
        return null;
    }

    private SystemMetrics.Memory sampleMemory() {
        if (systemInfo == null) return null;
        GlobalMemory global = systemInfo.getHardware().getMemory();
        long total = global.getTotal();
        long available = global.getAvailable();
        return new SystemMetrics.Memory(total - available, available, total);
    }

    private SystemMetrics.Disk sampleDisk() {
        if (operatingSystem == null) return null;
        OSFileStore store = fileStore();
        if (store == null) return null;
        long total = store.getTotalSpace();
        long available = store.getUsableSpace();
        return new SystemMetrics.Disk(Math.max(0, total - available), available, total);
    }

    private OSFileStore fileStore() {
        Path working = Path.of("").toAbsolutePath().normalize();
        OSFileStore best = null;
        for (OSFileStore store : operatingSystem.getFileSystem().getFileStores()) {
            String mount = store.getMount();
            if (mount == null || mount.isBlank()) continue;
            try {
                Path mountPath = Path.of(mount).toAbsolutePath().normalize();
                if (!working.startsWith(mountPath)) continue;
                if (best == null || mount.length() > best.getMount().length()) best = store;
            } catch (Exception ignored) {}
        }
        if (best != null) return best;
        List<OSFileStore> stores = operatingSystem.getFileSystem().getFileStores();
        return stores.isEmpty() ? null : stores.get(0);
    }

    private SystemMetrics.Network sampleNetwork() {
        if (systemInfo == null) return null;
        long received = 0;
        long sent = 0;
        for (NetworkIF networkIf : systemInfo.getHardware().getNetworkIFs(true)) {
            if (loopback(networkIf)) continue;
            networkIf.updateAttributes();
            received += Math.max(0, networkIf.getBytesRecv());
            sent += Math.max(0, networkIf.getBytesSent());
        }
        long now = System.nanoTime();
        long previousAt = previousNetworkAt;
        long previousReceived = previousBytesReceived;
        long previousSent = previousBytesSent;
        previousNetworkAt = now;
        previousBytesReceived = received;
        previousBytesSent = sent;
        if (previousAt == 0) return null;
        long elapsed = now - previousAt;
        return new SystemMetrics.Network(
            StatusRates.bytesPerSecond(previousReceived, received, elapsed),
            StatusRates.bytesPerSecond(previousSent, sent, elapsed)
        );
    }

    private static boolean loopback(NetworkIF networkIf) {
        try {
            return networkIf.queryNetworkInterface() != null && networkIf.queryNetworkInterface().isLoopback();
        } catch (Exception ignored) {
            String name = networkIf.getName();
            return name != null && (name.startsWith("lo") || name.toLowerCase().contains("loopback"));
        }
    }

    private RedisMetrics sampleRedis() {
        Long keys = RedisClient.dbSize();
        Long memory = RedisClient.usedMemory();
        if (keys == null && memory == null) return null;
        return new RedisMetrics(keys, memory);
    }
}
