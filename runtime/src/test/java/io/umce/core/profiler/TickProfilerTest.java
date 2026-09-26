package io.umce.core.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TickProfilerTest {
    @Test
    void reportsNearestRankPercentilesForTheRetainedWindow() {
        TickProfiler profiler = new TickProfiler(3);
        profiler.recordTick(1_000_000);
        profiler.recordTick(2_000_000);
        profiler.recordTick(3_000_000);
        profiler.recordTick(4_000_000);

        ProfileSnapshot result = profiler.snapshot();

        assertEquals(3, result.getSampleCount());
        assertEquals(3.0, result.getMeanMilliseconds(), 0.0001);
        assertEquals(3.0, result.getP50Milliseconds(), 0.0001);
        assertEquals(4.0, result.getP95Milliseconds(), 0.0001);
        assertEquals(4.0, result.getP99Milliseconds(), 0.0001);
    }

    @Test
    void emptyProfilerHasNoInventedSamples() {
        ProfileSnapshot snapshot = new TickProfiler(5).snapshot();
        assertEquals(0, snapshot.getSampleCount());
        assertEquals(0.0, snapshot.getMeanMilliseconds());
    }

    @Test
    void rejectsInvalidSamplesAndCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TickProfiler(0));
        assertThrows(IllegalArgumentException.class, () -> new TickProfiler(2).recordTick(-1));
    }
}
