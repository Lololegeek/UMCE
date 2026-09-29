package io.umce.platform.fabric.optimization;

public final class InsideWallLoopPatchRuntime {
    private static volatile boolean enabled;

    private InsideWallLoopPatchRuntime() { }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) { enabled = value; }
}
