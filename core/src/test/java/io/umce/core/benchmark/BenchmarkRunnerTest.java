package io.umce.core.benchmark;

import io.umce.core.hardware.HardwareDetector;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunnerTest {
    @Test
    void recordsWarmupsRawSamplesAndObservedHardware() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger executions = new AtomicInteger();
        BenchmarkRunner runner = new BenchmarkRunner(new HardwareDetector(), () -> clock.getAndAdd(200));

        BenchmarkResult result = runner.run("integer-sum", 100, 2, 3, operations -> {
            executions.incrementAndGet();
            long sum = 0;
            for (int i = 0; i < operations; i++) sum += i;
            return sum;
        });

        assertEquals("umce-benchmark-v1", result.getSchema());
        assertEquals(5, executions.get());
        assertEquals(3, result.getSamples().size());
        assertEquals(200, result.getMedianElapsedNanos());
        assertEquals(200, result.getP95ElapsedNanos());
        assertEquals(4_950, result.getSamples().get(0).getResultChecksum());
        assertTrue(result.getHardware().getLogicalProcessors() >= 1);
    }

    @Test
    void refusesInvalidWorkloadDimensions() {
        BenchmarkRunner runner = new BenchmarkRunner();
        assertThrows(IllegalArgumentException.class, () -> runner.run("../bad", 1, 0, 1, n -> n));
        assertThrows(IllegalArgumentException.class, () -> runner.run("valid", 0, 0, 1, n -> n));
    }
}
