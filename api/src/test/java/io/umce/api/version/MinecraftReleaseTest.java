package io.umce.api.version;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.compat.TestStatus;
import io.umce.api.platform.LoaderId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftReleaseTest {
    @Test
    void unknownMetadataIsAbsentAndCompatibilityDefaultsAreConservative() {
        MinecraftRelease release = MinecraftRelease.builder("26.3").build();

        assertFalse(release.getProtocolVersion().isPresent());
        assertFalse(release.getDataVersion().isPresent());
        assertTrue(release.getSupportedLoaders().isEmpty());
        assertEquals(CompatibilityStatus.UNKNOWN, release.getCompatibilityStatus());
        assertEquals(SupportStatus.PLANNED, release.getOptimizationSupport());
    }

    @Test
    void loaderVersionsAreImmutableAndImplyLoaderPresence() {
        MinecraftRelease release = MinecraftRelease.builder("1.20.1")
                .loaderVersion(LoaderId.FABRIC, "0.15.11")
                .build();

        assertTrue(release.getSupportedLoaders().contains(LoaderId.FABRIC));
        assertEquals("0.15.11", release.getLoaderVersions().get(LoaderId.FABRIC).iterator().next());
        assertThrows(UnsupportedOperationException.class,
                () -> release.getSupportedLoaders().add(LoaderId.FORGE));
    }

    @Test
    void adapterSupportIsTrackedIndependentlyPerLoader() {
        MinecraftRelease release = MinecraftRelease.builder("1.21.1")
                .adapterSupport(MinecraftAdapterSupport.builder(LoaderId.FABRIC)
                        .loaderVersion("0.16.14").adapterId("fabric-1.21.1")
                        .optimizationSupport(SupportStatus.PARTIAL).testStatus(TestStatus.PASS)
                        .compatibilityStatus(CompatibilityStatus.PARTIAL).build())
                .adapterSupport(MinecraftAdapterSupport.builder(LoaderId.NEOFORGE)
                        .loaderVersion("21.1.0").adapterId("neoforge-1.21.1")
                        .optimizationSupport(SupportStatus.PLANNED).testStatus(TestStatus.NOT_RUN)
                        .compatibilityStatus(CompatibilityStatus.UNKNOWN).build())
                .build();

        assertEquals(SupportStatus.PARTIAL,
                release.getAdapterSupport(LoaderId.FABRIC).get().getOptimizationSupport());
        assertEquals(SupportStatus.PLANNED,
                release.getAdapterSupport(LoaderId.NEOFORGE).get().getOptimizationSupport());
        assertFalse(release.getAdapterSupport(LoaderId.FORGE).isPresent());
        assertThrows(UnsupportedOperationException.class,
                () -> release.getAdapterSupports().clear());
    }
}
