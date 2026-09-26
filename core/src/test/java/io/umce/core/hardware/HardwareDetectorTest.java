package io.umce.core.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HardwareDetectorTest {
    @Test
    void reportsRuntimeFactsWithoutClaimingGpuSupport() {
        HardwareProfile profile = new HardwareDetector().detect();

        assertTrue(profile.getLogicalProcessors() >= 1);
        assertTrue(profile.getMaxHeapBytes() > 0);
        assertFalse(profile.getJvmVersion().trim().isEmpty());
        assertEquals("NOT_PROBED", profile.getGpuComputeProbe());
        assertThrows(UnsupportedOperationException.class, () -> profile.getGarbageCollectors().clear());
    }
}
