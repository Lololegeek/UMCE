package io.umce.platform.fabric;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.core.config.ConfigStore;
import io.umce.core.config.OptimizationMode;
import io.umce.core.config.OptimizationProfileSelector;
import io.umce.core.config.PatchPreference;
import io.umce.core.config.UmceConfig;
import io.umce.core.hardware.HardwareDetector;
import io.umce.core.hardware.HardwareProfile;
import io.umce.core.patch.PatchEngine;
import io.umce.core.patch.PatchOperationResult;
import io.umce.core.patch.PatchState;
import io.umce.platform.fabric.optimization.EmptyPassengerTrackDistancePatch;
import io.umce.platform.fabric.optimization.EntityQueryPatchRuntime;
import io.umce.platform.fabric.optimization.EntityTrackingPatchRuntime;
import io.umce.platform.fabric.optimization.SmallBoxSectionProbePatch;
import io.umce.platform.fabric.mixin.UmceMixinConfigPlugin;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FabricPatchManager implements AutoCloseable {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("UMCE");
    private final PatchEngine engine = new PatchEngine();
    private final List<OptimizationPatch> patches = new ArrayList<OptimizationPatch>();
    private final ConfigStore configStore;
    private final FabricPlatformAdapter adapter;
    private HardwareProfile hardware;
    private final OptimizationProfileSelector selector = new OptimizationProfileSelector();
    private UmceConfig configuration;
    private String configurationError;

    public FabricPatchManager(FabricPlatformAdapter adapter) {
        this.adapter = adapter;
        Path file = FabricLoader.getInstance().getConfigDir().resolve("umce.properties");
        this.configStore = new ConfigStore(file, Math.max(1, Runtime.getRuntime().availableProcessors()));
        register(new SmallBoxSectionProbePatch());
        register(new EmptyPassengerTrackDistancePatch());

        try {
            configuration = configStore.loadOrCreate();
            Map<String, PatchPreference> defaults = new LinkedHashMap<String, PatchPreference>();
            for (OptimizationPatch patch : patches) {
                String patchId = patch.getDescriptor().getId();
                PatchPreference defaultPreference = SmallBoxSectionProbePatch.ID.equals(patchId)
                        || EmptyPassengerTrackDistancePatch.ID.equals(patchId)
                        ? PatchPreference.OFF : PatchPreference.AUTO;
                defaults.put(patch.getDescriptor().getId(), defaultPreference);
            }
            configuration = configStore.ensurePatchPreferences(defaults);
        } catch (IOException exception) {
            configurationError = exception.getMessage();
            LOGGER.error("UMCE config is invalid; all gameplay patches remain disabled: {}", configurationError);
        }
        reconcile();
        LOGGER.info("UMCE config: mode={}, hardware={}, GPU={}", getMode(),
                hardware == null ? "not probed" : hardware.getLogicalProcessors() + " logical processors, max heap "
                        + hardware.getMaxHeapBytes() / (1024L * 1024L) + " MiB",
                configuration == null ? "unknown" : configuration.getGpuMode());
        if (configuration != null && "on".equals(configuration.getGpuMode())) {
            LOGGER.warn("GPU compute was requested, but UMCE has no GPU compute backend; GPU tasks remain disabled");
        }
    }

    public synchronized String getMode() {
        if (configuration == null) return "safe (config error)";
        String override = System.getProperty("umce.mode");
        if (override != null && !override.trim().isEmpty()) return resolveSystemMode(override).toConfigValue();
        return configuration.getOptimizationMode().toConfigValue();
    }

    public synchronized String getGpuMode() {
        return configuration == null ? "unknown" : configuration.getGpuMode();
    }

    public boolean hasGameplayMixinsLoaded() {
        return UmceMixinConfigPlugin.isSmallBoxSectionHookSelected()
                || UmceMixinConfigPlugin.isPassengerTrackingHookSelected();
    }

    public synchronized void setMode(String value) throws IOException {
        OptimizationMode requested = OptimizationMode.parse(value);
        UmceConfig current = requireConfig();
        configuration = configStore.saveOptimizationSettings(requested, current.getGpuMode(),
                current.getPatchPreferences());
        configurationError = null;
        reconcile();
    }

    public synchronized void reload() throws IOException {
        try {
            configuration = configStore.reload();
            configurationError = null;
            reconcile();
        } catch (IOException exception) {
            configuration = null;
            configurationError = exception.getMessage();
            reconcile();
            throw exception;
        }
    }

    public synchronized PatchOperationResult enable(String patchId) {
        return setManualPatch(patchId, PatchPreference.ON);
    }

    public synchronized PatchOperationResult disable(String patchId) {
        return setManualPatch(patchId, PatchPreference.OFF);
    }

    public synchronized String getEnabledPatchSummary() {
        List<String> enabled = new ArrayList<String>();
        for (OptimizationPatch patch : patches) {
            if (engine.getLastResult(patch.getDescriptor().getId()).getState() == PatchState.ENABLED) {
                enabled.add(patch.getDescriptor().getId());
            }
        }
        return enabled.isEmpty() ? "none" : String.join(", ", enabled);
    }

    public synchronized String getPatchSummary() {
        List<String> states = new ArrayList<String>();
        for (OptimizationPatch patch : patches) {
            String id = patch.getDescriptor().getId();
            PatchState state = engine.getLastResult(id).getState();
            states.add(id + "=" + state.name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", states);
    }

    public boolean isSmallBoxSectionProbeEnabled() {
        return EntityQueryPatchRuntime.isSmallBoxSectionProbeEnabled();
    }

    public boolean isEmptyPassengerTrackDistanceEnabled() {
        return EntityTrackingPatchRuntime.isEmptyPassengerTrackDistanceEnabled();
    }

    @Override public synchronized void close() { engine.close(); }

    private void register(OptimizationPatch patch) {
        patches.add(patch);
        engine.register(patch);
    }

    private PatchOperationResult setManualPatch(String patchId, PatchPreference preference) {
        OptimizationPatch patch = findPatch(patchId);
        if (configuration == null) {
            throw new IllegalStateException("Cannot change patches while UMCE config is invalid: " + configurationError);
        }
        Map<String, PatchPreference> preferences = new LinkedHashMap<String, PatchPreference>(
                configuration.getPatchPreferences());
        preferences.put(patchId, preference);
        try {
            configuration = configStore.saveOptimizationSettings(OptimizationMode.MANUAL,
                    configuration.getGpuMode(), preferences);
            configurationError = null;
            reconcile();
            return engine.getLastResult(patchId);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save UMCE config: " + exception.getMessage(), exception);
        }
    }

    private void reconcile() {
        OptimizationMode mode = effectiveMode();
        Map<String, String> settings = new LinkedHashMap<String, String>();
        settings.put("mode", mode == OptimizationMode.SAFE || configuration == null ? "safe" : "optimized");
        settings.put("entity-query-hook.available", Boolean.toString(UmceMixinConfigPlugin.isSmallBoxSectionHookSelected()));
        settings.put("passenger-tracking-hook.available", Boolean.toString(UmceMixinConfigPlugin.isPassengerTrackingHookSelected()));
        PatchContext context = new PatchContext(adapter, settings);
        HardwareProfile selectionHardware = mode == OptimizationMode.AUTO ? getHardwareProfile() : null;
        for (OptimizationPatch patch : patches) {
            PatchDescriptor descriptor = patch.getDescriptor();
            boolean selected = configuration != null && !UmceMixinConfigPlugin.isPassiveStartup() && selector.shouldEnable(mode,
                    patchPreference(descriptor.getId()), descriptor, selectionHardware);
            String systemOverride = System.getProperty("umce.patch." + descriptor.getId() + ".enabled");
            if (configuration != null && systemOverride != null) {
                if ("true".equalsIgnoreCase(systemOverride)) selected = mode != OptimizationMode.SAFE;
                else if ("false".equalsIgnoreCase(systemOverride)) selected = false;
            }

            if (selected) {
                PatchOperationResult result = engine.enable(descriptor.getId(), context,
                        CompatibilityStatus.SUPPORTED, true);
                if (result.getState() != PatchState.ENABLED) {
                    LOGGER.warn("Patch {} was not enabled: {}", descriptor.getId(), result.getEvaluation().getReason());
                }
            } else {
                engine.disable(descriptor.getId());
            }
        }
    }

    private HardwareProfile getHardwareProfile() {
        if (hardware == null) hardware = new HardwareDetector().detect();
        return hardware;
    }

    private PatchPreference patchPreference(String patchId) {
        PatchPreference preference = configuration.getPatchPreference(patchId);
        String systemOverride = System.getProperty("umce.patch." + patchId + ".enabled");
        if (systemOverride == null) return preference;
        if ("true".equalsIgnoreCase(systemOverride)) return PatchPreference.ON;
        if ("false".equalsIgnoreCase(systemOverride)) return PatchPreference.OFF;
        return preference;
    }

    private OptimizationMode effectiveMode() {
        if (configuration == null) return OptimizationMode.SAFE;
        String override = System.getProperty("umce.mode");
        return override == null || override.trim().isEmpty()
                ? configuration.getOptimizationMode() : resolveSystemMode(override);
    }

    private static OptimizationMode resolveSystemMode(String value) {
        if ("optimized".equalsIgnoreCase(value.trim())) return OptimizationMode.MANUAL;
        try {
            return OptimizationMode.parse(value);
        } catch (IOException exception) {
            LOGGER.warn("Ignoring invalid -Dumce.mode value '{}'; using safe mode", value);
            return OptimizationMode.SAFE;
        }
    }

    private OptimizationPatch findPatch(String patchId) {
        for (OptimizationPatch patch : patches) {
            if (patch.getDescriptor().getId().equals(patchId)) return patch;
        }
        throw new IllegalArgumentException("Unknown optimization patch: " + patchId);
    }

    private UmceConfig requireConfig() throws IOException {
        if (configuration == null) throw new IOException("UMCE config is invalid: " + configurationError);
        return configuration;
    }
}
