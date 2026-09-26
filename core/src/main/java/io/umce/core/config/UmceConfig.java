package io.umce.core.config;

public final class UmceConfig {
    private final String profile;
    private final int cpuWorkers;
    private final int cpuQueueCapacity;
    private final boolean gpuEnabled;
    private final boolean dashboardEnabled;
    private final String dashboardBind;
    private final boolean safeUnknownMods;

    UmceConfig(String profile, int cpuWorkers, int cpuQueueCapacity, boolean gpuEnabled,
                boolean dashboardEnabled, String dashboardBind, boolean safeUnknownMods) {
        this.profile = profile;
        this.cpuWorkers = cpuWorkers;
        this.cpuQueueCapacity = cpuQueueCapacity;
        this.gpuEnabled = gpuEnabled;
        this.dashboardEnabled = dashboardEnabled;
        this.dashboardBind = dashboardBind;
        this.safeUnknownMods = safeUnknownMods;
    }

    public String getProfile() { return profile; }
    public int getCpuWorkers() { return cpuWorkers; }
    public int getCpuQueueCapacity() { return cpuQueueCapacity; }
    public boolean isGpuEnabled() { return gpuEnabled; }
    public boolean isDashboardEnabled() { return dashboardEnabled; }
    public String getDashboardBind() { return dashboardBind; }
    public boolean isSafeUnknownMods() { return safeUnknownMods; }
}
