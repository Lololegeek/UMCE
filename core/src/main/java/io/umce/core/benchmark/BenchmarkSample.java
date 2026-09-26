package io.umce.core.benchmark;

public final class BenchmarkSample {
    private final long elapsedNanos;
    private final long resultChecksum;

    BenchmarkSample(long elapsedNanos, long resultChecksum) {
        this.elapsedNanos = elapsedNanos;
        this.resultChecksum = resultChecksum;
    }

    public long getElapsedNanos() { return elapsedNanos; }
    public long getResultChecksum() { return resultChecksum; }
}
