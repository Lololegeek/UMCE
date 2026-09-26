package io.umce.core.version;

import io.umce.api.platform.LoaderId;
import io.umce.api.version.MinecraftRelease;
import io.umce.api.version.SupportStatus;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionSupportCatalogTest {
    @Test
    void keepsOptimizationStatusSeparateForEachLoader() throws Exception {
        String rows = "1.21.1|fabric|0.16.14|fabric-1.21.1|21|PARTIAL|PASS|PARTIAL\n"
                + "1.21.1|neoforge|21.1.0|neoforge-1.21.1|21|PLANNED|NOT_RUN|UNKNOWN\n";
        VersionSupportCatalog catalog = VersionSupportCatalog.read(new StringReader(rows));
        MinecraftRelease.Builder builder = MinecraftRelease.builder("1.21.1");

        catalog.applyTo(builder, "1.21.1");
        MinecraftRelease release = builder.build();

        assertEquals(SupportStatus.PARTIAL,
                release.getAdapterSupport(LoaderId.FABRIC).get().getOptimizationSupport());
        assertEquals(SupportStatus.PLANNED,
                release.getAdapterSupport(LoaderId.NEOFORGE).get().getOptimizationSupport());
        assertTrue(release.getSupportedLoaders().contains(LoaderId.FABRIC));
        assertTrue(release.getSupportedLoaders().contains(LoaderId.NEOFORGE));
    }
}
