package io.umce.platform.fabric.optimization;

public final class PoiCandidateCollectionPatchRuntime {
    private static volatile boolean enabled;

    private PoiCandidateCollectionPatchRuntime() { }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) { enabled = value; }
}
