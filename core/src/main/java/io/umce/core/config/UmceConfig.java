package io.umce.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UmceConfig {
    private final String profile;
    private final int cpuWorkers;
    private final int cpuQueueCapacity;
    private final boolean gpuEnabled;
    private final boolean dashboardEnabled;
    private final String dashboardBind;
    private final boolean safeUnknownMods;
    private final OptimizationMode optimizationMode;
    private final String gpuMode;
    private final Map<String, PatchPreference> patchPreferences;

    UmceConfig(String profile, int cpuWorkers, int cpuQueueCapacity, boolean gpuEnabled,
                boolean dashboardEnabled, String dashboardBind, boolean safeUnknownMods,
                OptimizationMode optimizationMode, String gpuMode,
                Map<String, PatchPreference> patchPreferences) {
        this.profile = profile;
        this.cpuWorkers = cpuWorkers;
        this.cpuQueueCapacity = cpuQueueCapacity;
        this.gpuEnabled = gpuEnabled;
        this.dashboardEnabled = dashboardEnabled;
        this.dashboardBind = dashboardBind;
        this.safeUnknownMods = safeUnknownMods;
        this.optimizationMode = optimizationMode;
        this.gpuMode = gpuMode;
        this.patchPreferences = Collections.unmodifiableMap(
                new LinkedHashMap<String, PatchPreference>(patchPreferences));
    }

    public String getProfile() { return profile; }
    public int getCpuWorkers() { return cpuWorkers; }
    public int getCpuQueueCapacity() { return cpuQueueCapacity; }
    public boolean isGpuEnabled() { return gpuEnabled; }
    public boolean isDashboardEnabled() { return dashboardEnabled; }
    public String getDashboardBind() { return dashboardBind; }
    public boolean isSafeUnknownMods() { return safeUnknownMods; }
    public OptimizationMode getOptimizationMode() { return optimizationMode; }
    public String getGpuMode() { return gpuMode; }
    public Map<String, PatchPreference> getPatchPreferences() { return patchPreferences; }
    public PatchPreference getPatchPreference(String patchId) {
        PatchPreference preference = patchPreferences.get(patchId);
        return preference == null ? PatchPreference.AUTO : preference;
    }
}
