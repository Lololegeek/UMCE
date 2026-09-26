package io.umce.core.benchmark;

import io.umce.core.hardware.HardwareDetector;
import io.umce.core.hardware.HardwareProfile;

import java.util.ArrayList;
import java.util.List;

/** Repeated local workload runner; Minecraft before/after comparisons are a separate concern. */
public final class BenchmarkRunner {
    private final HardwareDetector hardwareDetector;
    private final NanoClock clock;

    public BenchmarkRunner() {
        this(new HardwareDetector(), new NanoClock() {
            @Override public long nanoTime() { return System.nanoTime(); }
        });
    }

    BenchmarkRunner(HardwareDetector hardwareDetector, NanoClock clock) {
        if (hardwareDetector == null || clock == null) throw new IllegalArgumentException("dependencies must not be null");
        this.hardwareDetector = hardwareDetector;
        this.clock = clock;
    }

    public BenchmarkResult run(String scenario, int operationsPerSample, int warmupIterations,
                               int sampleCount, BenchmarkTask task) throws Exception {
        if (scenario == null || !scenario.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) {
            throw new IllegalArgumentException("scenario must be a short id containing letters, digits, dot, underscore or dash");
        }
        if (operationsPerSample < 1) throw new IllegalArgumentException("operationsPerSample must be positive");
        if (warmupIterations < 0 || warmupIterations > 100_000) throw new IllegalArgumentException("warmupIterations out of range");
        if (sampleCount < 1 || sampleCount > 10_000) throw new IllegalArgumentException("sampleCount out of range");
        if (task == null) throw new IllegalArgumentException("task must not be null");

        for (int i = 0; i < warmupIterations; i++) task.run(operationsPerSample);
        long startedAt = System.currentTimeMillis();
        List<BenchmarkSample> samples = new ArrayList<BenchmarkSample>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            long start = clock.nanoTime();
            long checksum = task.run(operationsPerSample);
            long elapsed = clock.nanoTime() - start;
            if (elapsed <= 0) throw new IllegalStateException("Clock did not advance during benchmark sample " + (i + 1));
            samples.add(new BenchmarkSample(elapsed, checksum));
        }
        HardwareProfile profile = hardwareDetector.detect();
        return new BenchmarkResult(scenario, startedAt, operationsPerSample, warmupIterations, profile, samples);
    }

    interface NanoClock {
        long nanoTime();
    }
}
