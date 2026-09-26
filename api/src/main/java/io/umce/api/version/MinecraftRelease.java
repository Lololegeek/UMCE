package io.umce.api.version;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.compat.TestStatus;
import io.umce.api.platform.LoaderId;

import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Immutable version inventory entry. Missing upstream fields remain absent. */
public final class MinecraftRelease {
    private final String id;
    private final ReleaseType releaseType;
    private final Optional<Instant> releaseDate;
    private final OptionalInt protocolVersion;
    private final OptionalInt dataVersion;
    private final OptionalInt requiredJavaVersion;
    private final OptionalInt recommendedJavaVersion;
    private final List<String> availableMappings;
    private final Optional<URL> serverJar;
    private final Set<LoaderId> supportedLoaders;
    private final Map<LoaderId, Set<String>> loaderVersions;
    private final Map<LoaderId, MinecraftAdapterSupport> adapterSupports;
    private final Optional<String> adapterId;
    private final SupportStatus optimizationSupport;
    private final TestStatus testStatus;
    private final CompatibilityStatus compatibilityStatus;

    private MinecraftRelease(Builder builder) {
        this.id = requireText(builder.id, "id");
        this.releaseType = builder.releaseType;
        this.releaseDate = Optional.ofNullable(builder.releaseDate);
        this.protocolVersion = builder.protocolVersion;
        this.dataVersion = builder.dataVersion;
        this.requiredJavaVersion = builder.requiredJavaVersion;
        this.recommendedJavaVersion = builder.recommendedJavaVersion;
        this.availableMappings = Collections.unmodifiableList(new ArrayList<String>(builder.availableMappings));
        this.serverJar = Optional.ofNullable(builder.serverJar);
        EnumSet<LoaderId> loaders = builder.supportedLoaders.isEmpty()
                ? EnumSet.noneOf(LoaderId.class) : EnumSet.copyOf(builder.supportedLoaders);
        this.supportedLoaders = Collections.unmodifiableSet(loaders);
        EnumMap<LoaderId, Set<String>> versions = new EnumMap<LoaderId, Set<String>>(LoaderId.class);
        for (Map.Entry<LoaderId, Set<String>> entry : builder.loaderVersions.entrySet()) {
            versions.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue().isEmpty()
                    ? Collections.<String>emptySet() : new java.util.LinkedHashSet<String>(entry.getValue())));
        }
        this.loaderVersions = Collections.unmodifiableMap(versions);
        this.adapterSupports = Collections.unmodifiableMap(new EnumMap<LoaderId, MinecraftAdapterSupport>(builder.adapterSupports));
        this.adapterId = Optional.ofNullable(builder.adapterId);
        this.optimizationSupport = builder.optimizationSupport;
        this.testStatus = builder.testStatus;
        this.compatibilityStatus = builder.compatibilityStatus;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() { return id; }
    public ReleaseType getReleaseType() { return releaseType; }
    public Optional<Instant> getReleaseDate() { return releaseDate; }
    public OptionalInt getProtocolVersion() { return protocolVersion; }
    public OptionalInt getDataVersion() { return dataVersion; }
    public OptionalInt getRequiredJavaVersion() { return requiredJavaVersion; }
    public OptionalInt getRecommendedJavaVersion() { return recommendedJavaVersion; }
    public List<String> getAvailableMappings() { return availableMappings; }
    public Optional<URL> getServerJar() { return serverJar; }
    public Set<LoaderId> getSupportedLoaders() { return supportedLoaders; }
    public Map<LoaderId, Set<String>> getLoaderVersions() { return loaderVersions; }
    public Map<LoaderId, MinecraftAdapterSupport> getAdapterSupports() { return adapterSupports; }
    public Optional<MinecraftAdapterSupport> getAdapterSupport(LoaderId loader) {
        return Optional.ofNullable(adapterSupports.get(loader));
    }
    public Optional<String> getAdapterId() { return adapterId; }
    public SupportStatus getOptimizationSupport() { return optimizationSupport; }
    public TestStatus getTestStatus() { return testStatus; }
    public CompatibilityStatus getCompatibilityStatus() { return compatibilityStatus; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static final class Builder {
        private final String id;
        private ReleaseType releaseType = ReleaseType.RELEASE;
        private Instant releaseDate;
        private OptionalInt protocolVersion = OptionalInt.empty();
        private OptionalInt dataVersion = OptionalInt.empty();
        private OptionalInt requiredJavaVersion = OptionalInt.empty();
        private OptionalInt recommendedJavaVersion = OptionalInt.empty();
        private final List<String> availableMappings = new ArrayList<String>();
        private URL serverJar;
        private final Set<LoaderId> supportedLoaders = EnumSet.noneOf(LoaderId.class);
        private final Map<LoaderId, Set<String>> loaderVersions = new EnumMap<LoaderId, Set<String>>(LoaderId.class);
        private final Map<LoaderId, MinecraftAdapterSupport> adapterSupports = new EnumMap<LoaderId, MinecraftAdapterSupport>(LoaderId.class);
        private String adapterId;
        private SupportStatus optimizationSupport = SupportStatus.PLANNED;
        private TestStatus testStatus = TestStatus.NOT_RUN;
        private CompatibilityStatus compatibilityStatus = CompatibilityStatus.UNKNOWN;

        private Builder(String id) { this.id = id; }
        public Builder releaseType(ReleaseType value) { this.releaseType = require(value, "releaseType"); return this; }
        public Builder releaseDate(Instant value) { this.releaseDate = value; return this; }
        public Builder protocolVersion(int value) { this.protocolVersion = positive(value, "protocolVersion"); return this; }
        public Builder dataVersion(int value) { this.dataVersion = positive(value, "dataVersion"); return this; }
        public Builder requiredJavaVersion(int value) { this.requiredJavaVersion = positive(value, "requiredJavaVersion"); return this; }
        public Builder recommendedJavaVersion(int value) { this.recommendedJavaVersion = positive(value, "recommendedJavaVersion"); return this; }
        public Builder addMapping(String value) { availableMappings.add(requireText(value, "mapping")); return this; }
        public Builder serverJar(URL value) { this.serverJar = value; return this; }
        public Builder supportsLoader(LoaderId value) { supportedLoaders.add(require(value, "loader")); return this; }
        public Builder loaderVersion(LoaderId loader, String value) {
            loader = require(loader, "loader");
            Set<String> versions = loaderVersions.get(loader);
            if (versions == null) { versions = new java.util.LinkedHashSet<String>(); loaderVersions.put(loader, versions); }
            versions.add(requireText(value, "loader version"));
            supportedLoaders.add(loader);
            return this;
        }
        public Builder adapterSupport(MinecraftAdapterSupport value) {
            if (value == null) throw new IllegalArgumentException("adapterSupport must not be null");
            if (adapterSupports.putIfAbsent(value.getLoader(), value) != null) {
                throw new IllegalArgumentException("Duplicate adapter support for loader: " + value.getLoader().getId());
            }
            supportedLoaders.add(value.getLoader());
            value.getLoaderVersion().ifPresent(version -> loaderVersion(value.getLoader(), version));
            return this;
        }
        public Builder adapterId(String value) { this.adapterId = requireText(value, "adapterId"); return this; }
        public Builder optimizationSupport(SupportStatus value) { this.optimizationSupport = require(value, "optimizationSupport"); return this; }
        public Builder testStatus(TestStatus value) { this.testStatus = require(value, "testStatus"); return this; }
        public Builder compatibilityStatus(CompatibilityStatus value) { this.compatibilityStatus = require(value, "compatibilityStatus"); return this; }
        public MinecraftRelease build() { return new MinecraftRelease(this); }

        private static OptionalInt positive(int value, String field) {
            if (value < 1) { throw new IllegalArgumentException(field + " must be positive"); }
            return OptionalInt.of(value);
        }
        private static <T> T require(T value, String field) {
            if (value == null) { throw new IllegalArgumentException(field + " must not be null"); }
            return value;
        }
    }
}
