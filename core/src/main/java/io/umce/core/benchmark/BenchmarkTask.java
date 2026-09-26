package io.umce.core.benchmark;

public interface BenchmarkTask {
    /** Executes the scenario's requested operation count and returns a checksum/result token. */
    long run(int operations) throws Exception;
}
