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
    private static volatile boolean passiveStartup;

    @Override
    public void onLoad(String mixinPackage) {
        passiveStartup = Boolean.getBoolean("umce.passive");
        Properties configuration = readConfiguration();
        smallBoxSectionHookSelected = !passiveStartup
                && patchSelected("small-box-section-probe", "false", configuration);
        passengerTrackingHookSelected = !passiveStartup
                && patchSelected("empty-passenger-track-distance", "false", configuration);
    }

    public static boolean isSmallBoxSectionHookSelected() { return smallBoxSectionHookSelected; }
    public static boolean isPassengerTrackingHookSelected() { return passengerTrackingHookSelected; }
    public static boolean isPassiveStartup() { return passiveStartup; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".SectionedEntityCacheMixin")) return smallBoxSectionHookSelected;
        if (mixinClassName.endsWith(".EntityTrackerMixin")) return passengerTrackingHookSelected;
        if (mixinClassName.endsWith(".EntityQueryProfilerMixin")) {
            return !passiveStartup && Boolean.getBoolean("umce.entityQueryProfiler.enabled");
        }
        return !passiveStartup;
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
