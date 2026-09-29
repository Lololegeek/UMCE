package io.umce.platform.fabric.optimization;

public final class InventoryScanPatchRuntime {
    private static volatile boolean containerEmptyEnabled;
    private static volatile boolean hopperFullEnabled;
    private InventoryScanPatchRuntime() { }
    public static boolean isContainerEmptyEnabled() { return containerEmptyEnabled; }
    public static boolean isHopperFullEnabled() { return hopperFullEnabled; }
    public static void setContainerEmptyEnabled(boolean enabled) { containerEmptyEnabled = enabled; }
    public static void setHopperFullEnabled(boolean enabled) { hopperFullEnabled = enabled; }
}
