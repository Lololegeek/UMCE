package io.umce.core.benchmark;

import io.umce.core.hardware.HardwareProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Raw reproducibility record. It intentionally contains no claimed speedup. */
public final class BenchmarkResult {
    private final String schema = "umce-benchmark-v1";
    private final String scenario;
    private final long startedAtEpochMillis;
    private final int operationsPerSample;
    private final int warmupIterations;
    private final HardwareProfile hardware;
    private final List<BenchmarkSample> samples;
    private final long medianElapsedNanos;
    private final long p95ElapsedNanos;
    private final double medianOperationsPerSecond;

    BenchmarkResult(String scenario, long startedAtEpochMillis, int operationsPerSample,
                    int warmupIterations, HardwareProfile hardware, List<BenchmarkSample> samples) {
        this.scenario = scenario;
        this.startedAtEpochMillis = startedAtEpochMillis;
        this.operationsPerSample = operationsPerSample;
        this.warmupIterations = warmupIterations;
        this.hardware = hardware;
        this.samples = Collections.unmodifiableList(new ArrayList<BenchmarkSample>(samples));
        long[] durations = new long[samples.size()];
        for (int i = 0; i < samples.size(); i++) durations[i] = samples.get(i).getElapsedNanos();
        java.util.Arrays.sort(durations);
        this.medianElapsedNanos = percentile(durations, 0.50);
        this.p95ElapsedNanos = percentile(durations, 0.95);
        this.medianOperationsPerSecond = operationsPerSample * 1_000_000_000.0 / medianElapsedNanos;
    }

    public String getSchema() { return schema; }
    public String getScenario() { return scenario; }
    public long getStartedAtEpochMillis() { return startedAtEpochMillis; }
    public int getOperationsPerSample() { return operationsPerSample; }
    public int getWarmupIterations() { return warmupIterations; }
    public HardwareProfile getHardware() { return hardware; }
    public List<BenchmarkSample> getSamples() { return samples; }
    public long getMedianElapsedNanos() { return medianElapsedNanos; }
    public long getP95ElapsedNanos() { return p95ElapsedNanos; }
    public double getMedianOperationsPerSecond() { return medianOperationsPerSecond; }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}
