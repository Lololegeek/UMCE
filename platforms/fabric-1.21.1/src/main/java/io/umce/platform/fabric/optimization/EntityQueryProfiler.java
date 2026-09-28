package io.umce.platform.fabric.optimization;

import com.sun.management.ThreadMXBean;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.LongAdder;

/** Diagnostic-only query accounting. This class is first touched only when profiling is requested. */
public final class EntityQueryProfiler {
    private static final LongAdder vanillaQueries = new LongAdder();
    private static final LongAdder vanillaNanos = new LongAdder();
    private static final LongAdder vanillaExclusiveNanos = new LongAdder();
    private static final LongAdder vanillaAllocatedBytes = new LongAdder();
    private static final LongAdder patchQueries = new LongAdder();
    private static final LongAdder patchNanos = new LongAdder();
    private static final LongAdder patchExclusiveNanos = new LongAdder();
    private static final LongAdder patchAllocatedBytes = new LongAdder();
    private static final LongAdder fallbackQueries = new LongAdder();
    private static final LongAdder sectionProbes = new LongAdder();
    private static final LongAdder serverTicks = new LongAdder();
    private static final ThreadMXBean allocationBean = allocationBean();
    private static final ThreadLocal<Frames> frames = ThreadLocal.withInitial(Frames::new);

    private EntityQueryProfiler() { }

    public static void beginQuery() {
        Frames current = frames.get();
        int depth = current.depth++;
        current.ensure(depth);
        current.startedNanos[depth] = System.nanoTime();
        current.allocatedBefore[depth] = allocatedBytes();
        current.childNanos[depth] = 0L;
        current.accelerated[depth] = false;
        current.fallback[depth] = false;
        current.probes[depth] = 0L;
    }

    public static void markAccelerated(long probes) {
        Frames current = frames.get();
        if (current.depth == 0) return;
        int depth = current.depth - 1;
        current.accelerated[depth] = true;
        current.probes[depth] = probes;
    }

    public static void markFallback() {
        Frames current = frames.get();
        if (current.depth > 0) current.fallback[current.depth - 1] = true;
    }

    public static void endQuery() {
        Frames current = frames.get();
        if (current.depth == 0) return;
        int depth = --current.depth;
        long elapsed = Math.max(0L, System.nanoTime() - current.startedNanos[depth]);
        long exclusive = Math.max(0L, elapsed - current.childNanos[depth]);
        long allocated = allocatedBytes();
        long delta = current.allocatedBefore[depth] < 0L || allocated < current.allocatedBefore[depth]
                ? 0L : allocated - current.allocatedBefore[depth];
        if (current.accelerated[depth]) {
            patchQueries.increment();
            patchNanos.add(elapsed);
            patchExclusiveNanos.add(exclusive);
            patchAllocatedBytes.add(delta);
            sectionProbes.add(current.probes[depth]);
        } else {
            vanillaQueries.increment();
            vanillaNanos.add(elapsed);
            vanillaExclusiveNanos.add(exclusive);
            vanillaAllocatedBytes.add(delta);
            if (current.fallback[depth]) fallbackQueries.increment();
        }
        if (current.depth > 0) current.childNanos[current.depth - 1] += elapsed;
    }

    public static void recordServerTick() { serverTicks.increment(); }

    public static void logSummary(Logger logger) {
        long vanillaCount = vanillaQueries.sum();
        long patchCount = patchQueries.sum();
        long ticks = serverTicks.sum();
        logger.info("UMCE entity-query profile: vanillaQueries={}, vanillaMeanInclusiveNs={}, vanillaAllocatedBytesPerQuery={}, "
                        + "patchQueriesAccelerated={}, patchMeanInclusiveNs={}, patchAllocatedBytesPerQuery={}, directSectionProbes={}, fallbackToVanilla={}, "
                        + "queryExclusiveNsPerTick={}, profilingTicks={}, allocationProbe={}",
                vanillaCount, mean(vanillaNanos.sum(), vanillaCount), mean(vanillaAllocatedBytes.sum(), vanillaCount),
                patchCount, mean(patchNanos.sum(), patchCount), mean(patchAllocatedBytes.sum(), patchCount),
                sectionProbes.sum(), fallbackQueries.sum(),
                ticks == 0L ? 0L : (vanillaExclusiveNanos.sum() + patchExclusiveNanos.sum()) / ticks,
                ticks, allocationBean == null ? "unsupported" : "enabled");
        logger.info("UMCE entity-index profile: customIndexBuilds=0, maintenanceUpdates=0, inserts=0, removes=0, moves=0, "
                + "cacheInvalidations=0, indexLocks=0; patch reuses vanilla trackingSections");
    }

    private static long allocatedBytes() {
        if (allocationBean == null || !allocationBean.isThreadAllocatedMemoryEnabled()) return -1L;
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof ThreadMXBean)) return null;
        ThreadMXBean extended = (ThreadMXBean) bean;
        if (!extended.isThreadAllocatedMemorySupported()) return null;
        try {
            if (!extended.isThreadAllocatedMemoryEnabled()) extended.setThreadAllocatedMemoryEnabled(true);
            return extended.isThreadAllocatedMemoryEnabled() ? extended : null;
        } catch (SecurityException exception) {
            return null;
        }
    }

    private static long mean(long total, long count) { return count == 0L ? 0L : total / count; }

    private static final class Frames {
        private long[] startedNanos = new long[4];
        private long[] allocatedBefore = new long[4];
        private long[] probes = new long[4];
        private long[] childNanos = new long[4];
        private boolean[] accelerated = new boolean[4];
        private boolean[] fallback = new boolean[4];
        private int depth;

        private void ensure(int index) {
            if (index < startedNanos.length) return;
            int size = startedNanos.length * 2;
            while (size <= index) size *= 2;
            startedNanos = java.util.Arrays.copyOf(startedNanos, size);
            allocatedBefore = java.util.Arrays.copyOf(allocatedBefore, size);
            probes = java.util.Arrays.copyOf(probes, size);
            childNanos = java.util.Arrays.copyOf(childNanos, size);
            accelerated = java.util.Arrays.copyOf(accelerated, size);
            fallback = java.util.Arrays.copyOf(fallback, size);
        }
    }
}
