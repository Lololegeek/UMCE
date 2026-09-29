package io.umce.platform.fabric.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Keeps gameplay Mixins out of a passive server's transformed Minecraft classes. */
public final class UmceMixinConfigPlugin implements IMixinConfigPlugin {
    private static volatile boolean smallBoxSectionHookSelected;
    private static volatile boolean passengerTrackingHookSelected;
    private static volatile boolean insideWallHookSelected;
    private static volatile boolean poiCandidateCollectionHookSelected;
    private static volatile boolean brainTaskLaunchHookSelected;
    private static volatile boolean brainRunningTaskBufferHookSelected;
    private static volatile String brainRunningTaskBufferBlockers = "";
    private static volatile boolean containerEmptyScanHookSelected;
    private static volatile boolean hopperFullScanHookSelected;
    private static volatile String inventoryScanBlockers = "";
    private static volatile boolean passiveStartup;

    @Override
    public void onLoad(String mixinPackage) {
        passiveStartup = Boolean.getBoolean("umce.passive");
        Properties configuration = readConfiguration();
        smallBoxSectionHookSelected = !passiveStartup
                && patchSelected("small-box-section-probe", "off", configuration);
        passengerTrackingHookSelected = !passiveStartup
                && patchSelected("empty-passenger-track-distance", "off", configuration);
        // This experiment diverged from vanilla suffocation behavior, so no config may apply its Mixin.
        insideWallHookSelected = false;
        poiCandidateCollectionHookSelected = !passiveStartup
                && patchSelected("poi-candidate-collection", "off", configuration);
        brainTaskLaunchHookSelected = !passiveStartup
                && !FabricLoader.getInstance().isModLoaded("lithium")
                && patchSelected("brain-task-launch-cache", "off", configuration);
        boolean wantsRunningBuffer = !passiveStartup && patchSelected("brain-running-task-buffer", "off", configuration);
        boolean wantsEmptyScan = !passiveStartup && patchSelected("container-empty-scan", "off", configuration);
        boolean wantsFullScan = !passiveStartup && patchSelected("hopper-full-scan", "off", configuration);
        String blockers = wantsRunningBuffer || wantsEmptyScan || wantsFullScan ? unverifiedRootMods() : "";
        brainRunningTaskBufferBlockers = blockers;
        inventoryScanBlockers = blockers;
        brainRunningTaskBufferHookSelected = wantsRunningBuffer && blockers.isEmpty();
        containerEmptyScanHookSelected = wantsEmptyScan && blockers.isEmpty();
        hopperFullScanHookSelected = wantsFullScan && blockers.isEmpty();
    }

    public static boolean isSmallBoxSectionHookSelected() { return smallBoxSectionHookSelected; }
    public static boolean isPassengerTrackingHookSelected() { return passengerTrackingHookSelected; }
    public static boolean isInsideWallHookSelected() { return insideWallHookSelected; }
    public static boolean isPoiCandidateCollectionHookSelected() { return poiCandidateCollectionHookSelected; }
    public static boolean isBrainTaskLaunchHookSelected() { return brainTaskLaunchHookSelected; }
    public static boolean isBrainRunningTaskBufferHookSelected() { return brainRunningTaskBufferHookSelected; }
    public static String getBrainRunningTaskBufferBlockers() { return brainRunningTaskBufferBlockers; }
    public static boolean isContainerEmptyScanHookSelected() { return containerEmptyScanHookSelected; }
    public static boolean isHopperFullScanHookSelected() { return hopperFullScanHookSelected; }
    public static String getInventoryScanBlockers() { return inventoryScanBlockers; }
    public static boolean isPassiveStartup() { return passiveStartup; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".SectionedEntityCacheMixin")) return smallBoxSectionHookSelected;
        if (mixinClassName.endsWith(".EntityTrackerMixin")) return passengerTrackingHookSelected;
        if (mixinClassName.endsWith(".EntityInsideWallMixin")) return insideWallHookSelected;
        if (mixinClassName.endsWith(".FindPointOfInterestTaskMixin")) return poiCandidateCollectionHookSelected;
        if (mixinClassName.endsWith(".BrainTaskLaunchCacheMixin")) return brainTaskLaunchHookSelected;
        if (mixinClassName.endsWith(".BrainRunningTaskBufferMixin")) return brainRunningTaskBufferHookSelected;
        if (mixinClassName.endsWith(".ContainerEmptyScanMixin")) return containerEmptyScanHookSelected;
        if (mixinClassName.endsWith(".HopperFullScanMixin")) return hopperFullScanHookSelected;
        if (mixinClassName.endsWith(".EntityQueryProfilerMixin")) {
            return !passiveStartup && Boolean.getBoolean("umce.entityQueryProfiler.enabled");
        }
        return !passiveStartup;
    }

    private static String unverifiedRootMods() {
        Set<String> blockers = new java.util.TreeSet<>();
        for (net.fabricmc.loader.api.ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            net.fabricmc.loader.api.ModContainer root = mod;
            while (root.getContainingMod().isPresent()) root = root.getContainingMod().get();
            String id = root.getMetadata().getId();
            if (!"minecraft".equals(id) && !"java".equals(id) && !"fabricloader".equals(id)
                    && !"fabric-api".equals(id) && !"umce_fabric_1_21_1".equals(id)) blockers.add(id);
        }
        return String.join(", ", blockers);
    }

    private static boolean patchSelected(String id, String fallback, Properties properties) {
        String systemMode = System.getProperty("umce.mode");
        String mode = systemMode == null || systemMode.trim().isEmpty()
                ? normalizeMode(properties.getProperty("optimization.mode", "safe"))
                : normalizeMode(systemMode);
        String override = System.getProperty("umce.patch." + id + ".enabled");
        if (override != null) {
            if ("false".equalsIgnoreCase(override.trim())) return false;
            if ("true".equalsIgnoreCase(override.trim())) return !"safe".equals(mode);
        }
        String preference = properties.getProperty("optimization.patch." + id, fallback).trim().toLowerCase(java.util.Locale.ROOT);
        if ("safe".equals(mode) || "memory".equals(mode) || "off".equals(preference)) return false;
        if ("on".equals(preference)) return true;
        if ("manual".equals(mode)) return false;
        return "performance".equals(mode);
    }

    private static String normalizeMode(String raw) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return "optimized".equals(value) ? "manual" : value;
    }

    private static Properties readConfiguration() {
        Properties properties = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("umce.properties");
        if (!Files.isRegularFile(file)) return properties;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException ignored) {
            // Invalid or unreadable settings must not add hot-path Mixins.
            properties.clear();
        }
        return properties;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
