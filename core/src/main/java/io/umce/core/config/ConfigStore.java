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
import java.util.Properties;
import java.util.Set;

/** Strict, local properties configuration with atomic writes and explicit reload. */
public final class ConfigStore {
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(
            "profile", "cpu.workers", "cpu.queueCapacity", "gpu.enabled",
            "dashboard.enabled", "dashboard.bind", "compatibility.safeUnknownMods"));
    private final Path file;
    private final int availableProcessors;

    public ConfigStore(Path file, int availableProcessors) {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        if (availableProcessors < 1) throw new IllegalArgumentException("availableProcessors must be positive");
        this.file = file;
        this.availableProcessors = availableProcessors;
    }

    public synchronized UmceConfig loadOrCreate() throws IOException {
        if (!Files.exists(file)) write(defaultProperties());
        return load();
    }

    /** Reads changes made by an operator and returns a new immutable snapshot. */
    public synchronized UmceConfig reload() throws IOException {
        return load();
    }

    public Path getFile() {
        return file;
    }

    private UmceConfig load() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Configuration file does not exist: " + file);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) throw new IOException("Unknown UMCE configuration key: " + key);
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
        return new UmceConfig(profile, workers, queueCapacity, gpuEnabled,
                dashboardEnabled, bind, safeUnknownMods);
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
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
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
        return properties;
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
