package io.umce.api.version;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.compat.TestStatus;
import io.umce.api.platform.LoaderId;

import java.util.Optional;
import java.util.OptionalInt;

/** Verified status for one Minecraft release and one loader adapter. */
public final class MinecraftAdapterSupport {
    private final LoaderId loader;
    private final Optional<String> loaderVersion;
    private final Optional<String> adapterId;
    private final OptionalInt requiredJavaVersion;
    private final SupportStatus optimizationSupport;
    private final TestStatus testStatus;
    private final CompatibilityStatus compatibilityStatus;

    private MinecraftAdapterSupport(Builder builder) {
        this.loader = builder.loader;
        this.loaderVersion = Optional.ofNullable(builder.loaderVersion);
        this.adapterId = Optional.ofNullable(builder.adapterId);
        this.requiredJavaVersion = builder.requiredJavaVersion;
        this.optimizationSupport = builder.optimizationSupport;
        this.testStatus = builder.testStatus;
        this.compatibilityStatus = builder.compatibilityStatus;
    }

    public static Builder builder(LoaderId loader) { return new Builder(loader); }

    public LoaderId getLoader() { return loader; }
    public Optional<String> getLoaderVersion() { return loaderVersion; }
    public Optional<String> getAdapterId() { return adapterId; }
    public OptionalInt getRequiredJavaVersion() { return requiredJavaVersion; }
    public SupportStatus getOptimizationSupport() { return optimizationSupport; }
    public TestStatus getTestStatus() { return testStatus; }
    public CompatibilityStatus getCompatibilityStatus() { return compatibilityStatus; }

    public static final class Builder {
        private final LoaderId loader;
        private String loaderVersion;
        private String adapterId;
        private OptionalInt requiredJavaVersion = OptionalInt.empty();
        private SupportStatus optimizationSupport = SupportStatus.PLANNED;
        private TestStatus testStatus = TestStatus.NOT_RUN;
        private CompatibilityStatus compatibilityStatus = CompatibilityStatus.UNKNOWN;

        private Builder(LoaderId loader) {
            if (loader == null || loader == LoaderId.OTHER) throw new IllegalArgumentException("loader must be a known loader");
            this.loader = loader;
        }

        public Builder loaderVersion(String value) { this.loaderVersion = text(value, "loaderVersion"); return this; }
        public Builder adapterId(String value) { this.adapterId = text(value, "adapterId"); return this; }
        public Builder requiredJavaVersion(int value) {
            if (value < 1) throw new IllegalArgumentException("requiredJavaVersion must be positive");
            this.requiredJavaVersion = OptionalInt.of(value);
            return this;
        }
        public Builder optimizationSupport(SupportStatus value) { this.optimizationSupport = require(value, "optimizationSupport"); return this; }
        public Builder testStatus(TestStatus value) { this.testStatus = require(value, "testStatus"); return this; }
        public Builder compatibilityStatus(CompatibilityStatus value) { this.compatibilityStatus = require(value, "compatibilityStatus"); return this; }
        public MinecraftAdapterSupport build() { return new MinecraftAdapterSupport(this); }

        private static String text(String value, String field) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
            return value.trim();
        }

        private static <T> T require(T value, String field) {
            if (value == null) throw new IllegalArgumentException(field + " must not be null");
            return value;
        }
    }
}
