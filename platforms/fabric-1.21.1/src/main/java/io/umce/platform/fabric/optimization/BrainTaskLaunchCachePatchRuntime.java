package io.umce.platform.fabric.optimization;

public final class BrainTaskLaunchCachePatchRuntime {
    private static volatile boolean enabled;

    private BrainTaskLaunchCachePatchRuntime() { }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) { enabled = value; }
}
