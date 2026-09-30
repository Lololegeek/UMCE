package io.umce.benchmark;

import com.sun.management.ThreadMXBean;
import java.io.BufferedWriter;
import java.lang.instrument.Instrumentation;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Diagnostic-only agent; no transformers, Minecraft hooks, or gameplay access. */
public final class VmMetricsAgent {
    public static void premain(String output, Instrumentation ignored) {
        java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
        if (!(base instanceof ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("VM allocation counters are unsupported");
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        Path file = Path.of(output == null || output.isEmpty() ? "umce-vm-metrics.csv" : output);
        Thread monitor = new Thread(() -> sample(bean, collectors, file), "UMCE benchmark VM metrics");
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void sample(ThreadMXBean bean, List<GarbageCollectorMXBean> collectors, Path file) {
        Map<Long, Long> previous = new HashMap<>();
        long observedTotal = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("time_utc,observed_allocated_bytes,server_thread_allocated_bytes,gc_collections,gc_collection_time_ms\n");
            while (true) {
                long[] ids = bean.getAllThreadIds();
                if (ids.length > 8192) throw new IllegalStateException("Diagnostic thread sampling limit exceeded");
                long[] allocations = bean.getThreadAllocatedBytes(ids);
                ThreadInfo[] info = bean.getThreadInfo(ids);
                Set<Long> live = new HashSet<>();
                long serverBytes = -1;
                for (int index = 0; index < ids.length; index++) {
                    long id = ids[index];
                    long bytes = allocations[index];
                    live.add(id);
                    if (bytes >= 0) {
                        Long before = previous.put(id, bytes);
                        observedTotal += Math.max(0, bytes - (before == null ? 0 : before));
                        if (info[index] != null && "Server thread".equals(info[index].getThreadName())) serverBytes = bytes;
                    }
                }
                previous.keySet().retainAll(live);
                long collections = 0;
                long gcMillis = 0;
                boolean supported = true;
                for (GarbageCollectorMXBean collector : collectors) {
                    long count = collector.getCollectionCount();
                    long time = collector.getCollectionTime();
                    supported &= count >= 0 && time >= 0;
                    collections += Math.max(0, count);
                    gcMillis += Math.max(0, time);
                }
                writer.write(Instant.now() + "," + observedTotal + "," + serverBytes + ","
                        + (supported ? collections : -1) + "," + (supported ? gcMillis : -1) + "\n");
                writer.flush();
                Thread.sleep(1000);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            System.err.println("UMCE VM metric capture failed: " + failure);
        }
    }
}
