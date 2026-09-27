package io.umce.platform.fabric.optimization;

public final class EntityQueryPatchRuntime {
    private static volatile boolean smallBoxSectionProbeEnabled;

    private EntityQueryPatchRuntime() { }

    public static boolean isSmallBoxSectionProbeEnabled() {
        return smallBoxSectionProbeEnabled;
    }

    public static void setSmallBoxSectionProbeEnabled(boolean enabled) {
        smallBoxSectionProbeEnabled = enabled;
    }
}
