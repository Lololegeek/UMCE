package io.umce.core.profiler;

import java.util.Arrays;

/** Fixed-capacity rolling tick-duration sampler; it never stores world objects. */
public final class TickProfiler {
    private final long[] durationsNanos;
    private int nextIndex;
    private int size;

    public TickProfiler(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.durationsNanos = new long[capacity];
    }

    public synchronized void recordTick(long durationNanos) {
        if (durationNanos < 0) throw new IllegalArgumentException("durationNanos must not be negative");
        durationsNanos[nextIndex] = durationNanos;
        nextIndex = (nextIndex + 1) % durationsNanos.length;
        if (size < durationsNanos.length) size++;
    }

    public synchronized ProfileSnapshot snapshot() {
        if (size == 0) return new ProfileSnapshot(0, 0, 0, 0, 0, 0);
        long[] ordered = new long[size];
        int oldest = size == durationsNanos.length ? nextIndex : 0;
        for (int i = 0; i < size; i++) ordered[i] = durationsNanos[(oldest + i) % durationsNanos.length];
        long[] sorted = ordered.clone();
        Arrays.sort(sorted);
        double total = 0;
        for (long sample : ordered) total += sample;
        return new ProfileSnapshot(size,
                nanosToMillis(total / size),
                nanosToMillis(percentile(sorted, 0.50)),
                nanosToMillis(percentile(sorted, 0.95)),
                nanosToMillis(percentile(sorted, 0.99)),
                nanosToMillis(sorted[sorted.length - 1]));
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }
}
