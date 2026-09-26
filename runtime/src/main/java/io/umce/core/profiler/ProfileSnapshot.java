package io.umce.core.profiler;

public final class ProfileSnapshot {
    private final int sampleCount;
    private final double meanMilliseconds;
    private final double p50Milliseconds;
    private final double p95Milliseconds;
    private final double p99Milliseconds;
    private final double maxMilliseconds;

    ProfileSnapshot(int sampleCount, double meanMilliseconds, double p50Milliseconds,
                    double p95Milliseconds, double p99Milliseconds, double maxMilliseconds) {
        this.sampleCount = sampleCount;
        this.meanMilliseconds = meanMilliseconds;
        this.p50Milliseconds = p50Milliseconds;
        this.p95Milliseconds = p95Milliseconds;
        this.p99Milliseconds = p99Milliseconds;
        this.maxMilliseconds = maxMilliseconds;
    }

    public int getSampleCount() { return sampleCount; }
    public double getMeanMilliseconds() { return meanMilliseconds; }
    public double getP50Milliseconds() { return p50Milliseconds; }
    public double getP95Milliseconds() { return p95Milliseconds; }
    public double getP99Milliseconds() { return p99Milliseconds; }
    public double getMaxMilliseconds() { return maxMilliseconds; }
}
