package io.umce.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Strict, local properties configuration with atomic writes and explicit reload. */
public final class ConfigStore {
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(
            "profile", "cpu.workers", "cpu.queueCapacity", "gpu.enabled",
            "dashboard.enabled", "dashboard.bind", "compatibility.safeUnknownMods",
            "optimization.mode", "optimization.gpu"));
    private final Path file;
    private final int availableProcessors;

    public ConfigStore(Path file, int availableProcessors) {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (availableProcessors < 1) throw new IllegalArgumentException("availableProcessors must be positive");
        this.file = file;
        this.availableProcessors = availableProcessors;
    }

    public synchronized UmceConfig loadOrCreate() throws IOException {
        Properties defaults = defaultProperties();
        if (!Files.exists(file)) {
            write(defaults);
        } else {
            Properties existing = readProperties();
            boolean changed = false;
            for (String key : defaults.stringPropertyNames()) {
                if (!existing.containsKey(key)) {
                    existing.setProperty(key, defaults.getProperty(key));
                    changed = true;
                }
            }
            if (changed) write(existing);
        }
        return load();
    }

    /** Reads changes made by an operator and returns a new immutable snapshot. */
    public synchronized UmceConfig reload() throws IOException {
        return load();
    }

    public synchronized UmceConfig saveOptimizationSettings(OptimizationMode mode, String gpuMode,
                                                             Map<String, PatchPreference> patchPreferences)
            throws IOException {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        if (patchPreferences == null) throw new IllegalArgumentException("patchPreferences must not be null");
        String normalizedGpuMode = normalizeGpuMode(gpuMode);
        Properties properties = readProperties();
        properties.setProperty("optimization.mode", mode.toConfigValue());
        properties.setProperty("optimization.gpu", normalizedGpuMode);
        for (Map.Entry<String, PatchPreference> entry : patchPreferences.entrySet()) {
            validatePatchId(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("patch preference must not be null");
            properties.setProperty("optimization.patch." + entry.getKey(), entry.getValue().toConfigValue());
        }
        write(properties);
        return load();
    }

    /** Adds defaults for adapter-provided patch ids without hard-coding those ids in core. */
    public synchronized UmceConfig ensurePatchPreferences(Map<String, PatchPreference> defaults)
            throws IOException {
        if (defaults == null) throw new IllegalArgumentException("defaults must not be null");
        Properties properties = readProperties();
        boolean changed = false;
        for (Map.Entry<String, PatchPreference> entry : defaults.entrySet()) {
            validatePatchId(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("patch default must not be null");
            String key = "optimization.patch." + entry.getKey();
            if (!properties.containsKey(key)) {
                properties.setProperty(key, entry.getValue().toConfigValue());
                changed = true;
            }
        }
        if (changed) write(properties);
        return load();
    }

    public Path getFile() {
        return file;
    }

    private UmceConfig load() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Configuration file does not exist: " + file);
        Properties properties = readProperties();
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key) && !isPatchPreferenceKey(key)) {
                throw new IOException("Unknown UMCE configuration key: " + key);
            }
        }
        String profile = required(properties, "profile");
        if (!profile.matches("[a-z][a-z0-9_-]{0,31}")) throw new IOException("Invalid profile name: " + profile);
        int defaultWorkers = Math.max(1, Math.min(256, availableProcessors - 1));
        int workers = positiveInteger(properties, "cpu.workers", defaultWorkers, 256);
        int queueCapacity = positiveInteger(properties, "cpu.queueCapacity", 1024, 1_000_000);
        boolean gpuEnabled = strictBoolean(properties, "gpu.enabled", false);
        boolean dashboardEnabled = strictBoolean(properties, "dashboard.enabled", false);
        String bind = properties.getProperty("dashboard.bind", "127.0.0.1").trim();
        if (!("127.0.0.1".equals(bind) || "localhost".equalsIgnoreCase(bind)
                || "::1".equals(bind) || "[::1]".equals(bind))) {
            throw new IOException("Dashboard bind must be loopback-only");
        }
        boolean safeUnknownMods = strictBoolean(properties, "compatibility.safeUnknownMods", true);
        OptimizationMode optimizationMode = OptimizationMode.parse(
                properties.getProperty("optimization.mode", "safe"));
        String gpuMode = normalizeGpuMode(properties.getProperty("optimization.gpu", "auto"));
        Map<String, PatchPreference> patchPreferences = new LinkedHashMap<String, PatchPreference>();
        for (String key : properties.stringPropertyNames()) {
            if (isPatchPreferenceKey(key)) {
                String patchId = key.substring("optimization.patch.".length());
                validatePatchId(patchId);
                patchPreferences.put(patchId, PatchPreference.parse(properties.getProperty(key), key));
            }
        }
        return new UmceConfig(profile, workers, queueCapacity, gpuEnabled,
                dashboardEnabled, bind, safeUnknownMods, optimizationMode, gpuMode, patchPreferences);
    }

    private Properties readProperties() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Configuration file does not exist: " + file);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        return properties;
    }

    private void write(Properties properties) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "umce-config-", ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "UMCE server configuration. Unknown compatibility is conservative.");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("profile", "balanced");
        properties.setProperty("cpu.workers", Integer.toString(Math.max(1, Math.min(256, availableProcessors - 1))));
        properties.setProperty("cpu.queueCapacity", "1024");
        properties.setProperty("gpu.enabled", "false");
        properties.setProperty("dashboard.enabled", "false");
        properties.setProperty("dashboard.bind", "127.0.0.1");
        properties.setProperty("compatibility.safeUnknownMods", "true");
        properties.setProperty("optimization.mode", "safe");
        properties.setProperty("optimization.gpu", "auto");
        return properties;
    }

    private static boolean isPatchPreferenceKey(String key) {
        return key.startsWith("optimization.patch.");
    }

    private static void validatePatchId(String patchId) throws IOException {
        if (patchId == null || !patchId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IOException("Invalid optimization patch id: " + patchId);
        }
    }

    private static String normalizeGpuMode(String value) throws IOException {
        if (value == null) return "auto";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!("auto".equals(normalized) || "on".equals(normalized) || "off".equals(normalized))) {
            throw new IOException("Invalid optimization.gpu: " + value + " (expected auto, on, or off)");
        }
        return normalized;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) throw new IOException("Missing configuration key: " + key);
        return value.trim();
    }

    private static int positiveInteger(Properties properties, String key, int fallback, int max) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1 || parsed > max) throw new NumberFormatException("out of range");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid integer for " + key + ": " + value, exception);
        }
    }

    private static boolean strictBoolean(Properties properties, String key, boolean fallback) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value.trim())) return true;
        if ("false".equalsIgnoreCase(value.trim())) return false;
        throw new IOException("Expected true or false for " + key + ": " + value);
    }
}
