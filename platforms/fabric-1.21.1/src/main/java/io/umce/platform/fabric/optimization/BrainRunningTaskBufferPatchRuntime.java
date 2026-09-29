package io.umce.platform.fabric.optimization;

public final class BrainRunningTaskBufferPatchRuntime {
    private static volatile boolean enabled;
    private BrainRunningTaskBufferPatchRuntime() { }
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
}
