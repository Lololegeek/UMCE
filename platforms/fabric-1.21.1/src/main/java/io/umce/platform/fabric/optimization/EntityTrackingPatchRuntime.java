package io.umce.platform.fabric.optimization;

public final class EntityTrackingPatchRuntime {
    private static volatile boolean emptyPassengerTrackDistanceEnabled;

    private EntityTrackingPatchRuntime() { }

    public static boolean isEmptyPassengerTrackDistanceEnabled() {
        return emptyPassengerTrackDistanceEnabled;
    }

    public static void setEmptyPassengerTrackDistanceEnabled(boolean enabled) {
        emptyPassengerTrackDistanceEnabled = enabled;
    }
}
