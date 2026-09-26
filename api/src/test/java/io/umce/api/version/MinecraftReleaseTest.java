package io.umce.api.version;

import io.umce.api.compat.CompatibilityStatus;
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
}
