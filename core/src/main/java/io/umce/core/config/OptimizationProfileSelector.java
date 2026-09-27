package io.umce.core.config;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.PatchDescriptor;
import io.umce.core.hardware.HardwareProfile;

/** Chooses patch defaults for a profile while leaving explicit safety gates to PatchEngine. */
public final class OptimizationProfileSelector {
    private static final long MIN_AUTO_HEAP_BYTES = 1024L * 1024L * 1024L;

    public boolean shouldEnable(OptimizationMode mode, PatchPreference preference,
                                PatchDescriptor descriptor, HardwareProfile hardware) {
        if (mode == null || preference == null || descriptor == null || hardware == null) {
            throw new IllegalArgumentException("mode, preference, descriptor, and hardware must not be null");
        }
        if (mode == OptimizationMode.SAFE) return false;
        if (mode == OptimizationMode.MANUAL) return preference == PatchPreference.ON;
        if (mode == OptimizationMode.MEMORY) {
            return descriptor.getCategory() == OptimizationCategory.MEMORY
                    && preference != PatchPreference.OFF;
        }
        if (preference == PatchPreference.OFF) return false;
        if (preference == PatchPreference.ON || mode == OptimizationMode.PERFORMANCE) return true;
        if (!descriptor.isAutoEligible()) return false;
        if (mode == OptimizationMode.AUTO) {
            return hardware.getLogicalProcessors() >= 2
                    && hardware.getMaxHeapBytes() >= MIN_AUTO_HEAP_BYTES;
        }
        return mode == OptimizationMode.BALANCED;
    }
}
