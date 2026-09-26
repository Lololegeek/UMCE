package io.umce.core.version;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.compat.TestStatus;
import io.umce.api.platform.LoaderId;
import io.umce.api.version.MinecraftRelease;
import io.umce.api.version.SupportStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verified per-version/loader facts. Version inventory itself remains Mojang-driven. */
public final class VersionSupportCatalog {
    private static final String RESOURCE = "/umce/version-support.csv";
    private final Map<String, List<AdapterSupport>> supportByVersion;

    private VersionSupportCatalog(Map<String, List<AdapterSupport>> supportByVersion) {
        Map<String, List<AdapterSupport>> immutable = new LinkedHashMap<String, List<AdapterSupport>>();
        for (Map.Entry<String, List<AdapterSupport>> entry : supportByVersion.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<AdapterSupport>(entry.getValue())));
        }
        this.supportByVersion = Collections.unmodifiableMap(immutable);
    }

    public static VersionSupportCatalog empty() {
        return new VersionSupportCatalog(Collections.<String, List<AdapterSupport>>emptyMap());
    }

    public static VersionSupportCatalog fromClasspath() {
        InputStream input = VersionSupportCatalog.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing verified adapter catalog resource: " + RESOURCE);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read verified adapter catalog", exception);
        }
    }

    public static VersionSupportCatalog read(Reader reader) throws IOException {
        if (reader == null) throw new IllegalArgumentException("reader must not be null");
        Map<String, List<AdapterSupport>> byVersion = new LinkedHashMap<String, List<AdapterSupport>>();
        Set<String> uniqueAdapters = new HashSet<String>();
        BufferedReader buffered = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        int lineNumber = 0;
        while ((line = buffered.readLine()) != null) {
            lineNumber++;
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            String[] fields = value.split("\\|", -1);
            if (fields.length != 8) throw new IOException("Expected 8 support catalog fields at line " + lineNumber);
            try {
                AdapterSupport support = new AdapterSupport(fields);
                String key = support.minecraftVersion + "/" + support.loader.getId();
                if (!uniqueAdapters.add(key)) throw new IOException("Duplicate loader support entry at line " + lineNumber + ": " + key);
                List<AdapterSupport> entries = byVersion.get(support.minecraftVersion);
                if (entries == null) { entries = new ArrayList<AdapterSupport>(); byVersion.put(support.minecraftVersion, entries); }
                entries.add(support);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid adapter support data at line " + lineNumber, exception);
            }
        }
        return new VersionSupportCatalog(byVersion);
    }

    public List<AdapterSupport> get(String minecraftVersion) {
        List<AdapterSupport> support = supportByVersion.get(minecraftVersion);
        return support == null ? Collections.<AdapterSupport>emptyList() : support;
    }

    public void applyTo(MinecraftRelease.Builder builder, String minecraftVersion) {
        List<AdapterSupport> supports = get(minecraftVersion);
        if (supports.isEmpty()) return;
        AdapterSupport primary = supports.get(0);
        for (AdapterSupport support : supports) {
            builder.supportsLoader(support.loader)
                    .loaderVersion(support.loader, support.loaderVersion);
        }
        builder.adapterId(primary.adapterId)
                .optimizationSupport(primary.optimizationSupport)
                .testStatus(primary.testStatus)
                .compatibilityStatus(primary.compatibilityStatus);
        if (primary.requiredJavaVersion > 0) builder.requiredJavaVersion(primary.requiredJavaVersion);
    }

    public static final class AdapterSupport {
        private final String minecraftVersion;
        private final LoaderId loader;
        private final String loaderVersion;
        private final String adapterId;
        private final int requiredJavaVersion;
        private final SupportStatus optimizationSupport;
        private final TestStatus testStatus;
        private final CompatibilityStatus compatibilityStatus;

        private AdapterSupport(String[] fields) {
            this.minecraftVersion = text(fields[0], "Minecraft version");
            this.loader = LoaderId.fromId(text(fields[1], "loader"));
            if (loader == LoaderId.OTHER) throw new IllegalArgumentException("Unknown loader id: " + fields[1]);
            this.loaderVersion = text(fields[2], "loader version");
            this.adapterId = text(fields[3], "adapter id");
            this.requiredJavaVersion = fields[4].trim().isEmpty() || "-".equals(fields[4].trim())
                    ? 0 : parsePositive(fields[4], "required Java version");
            this.optimizationSupport = SupportStatus.valueOf(text(fields[5], "optimization support"));
            this.testStatus = TestStatus.valueOf(text(fields[6], "test status"));
            this.compatibilityStatus = CompatibilityStatus.valueOf(text(fields[7], "compatibility status"));
        }

        public String getMinecraftVersion() { return minecraftVersion; }
        public LoaderId getLoader() { return loader; }
        public String getLoaderVersion() { return loaderVersion; }
        public String getAdapterId() { return adapterId; }
        public int getRequiredJavaVersion() { return requiredJavaVersion; }
        public SupportStatus getOptimizationSupport() { return optimizationSupport; }
        public TestStatus getTestStatus() { return testStatus; }
        public CompatibilityStatus getCompatibilityStatus() { return compatibilityStatus; }

        private static int parsePositive(String value, String field) {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1) throw new IllegalArgumentException(field + " must be positive");
            return parsed;
        }

        private static String text(String value, String field) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
            return value.trim();
        }
    }
}
