package io.umce.platform.fabric.optimization;

public final class EntityQueryPatchRuntime {
    private static volatile boolean smallBoxSectionProbeEnabled;
    private static final boolean profilingEnabled = Boolean.getBoolean("umce.entityQueryProfiler.enabled")
            && !Boolean.getBoolean("umce.passive");

    private EntityQueryPatchRuntime() { }

    public static boolean isSmallBoxSectionProbeEnabled() {
        return smallBoxSectionProbeEnabled;
    }

    public static void setSmallBoxSectionProbeEnabled(boolean enabled) {
        smallBoxSectionProbeEnabled = enabled;
    }

    public static boolean isProfilingEnabled() { return profilingEnabled; }
}
