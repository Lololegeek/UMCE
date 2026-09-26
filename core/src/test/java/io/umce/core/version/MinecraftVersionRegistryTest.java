package io.umce.core.version;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.compat.TestStatus;
import io.umce.api.platform.LoaderId;
import io.umce.api.version.MinecraftRelease;
import io.umce.api.version.SupportStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftVersionRegistryTest {
    private static final URI INDEX = URI.create("https://metadata.example.test/manifest.json");

    @Test
    void registryIndexesOfficialStableEntriesAndOnlyCataloguedSupport() throws Exception {
        FixtureFetcher fetcher = new FixtureFetcher();
        MinecraftVersionRegistry registry = new MinecraftVersionRegistry(INDEX, fetcher);

        List<MinecraftRelease> releases = registry.refresh();

        assertEquals(2, releases.size());
        assertEquals("1.21.1", releases.get(0).getId());
        assertEquals("1.6.4", releases.get(1).getId());
        assertEquals(CompatibilityStatus.PARTIAL, releases.get(0).getCompatibilityStatus());
        assertFalse(releases.get(0).getProtocolVersion().isPresent());
        assertFalse(releases.get(0).getDataVersion().isPresent());
        assertEquals(LoaderId.FABRIC, releases.get(0).getSupportedLoaders().iterator().next());
        assertEquals("0.16.14", releases.get(0).getLoaderVersions().get(LoaderId.FABRIC).iterator().next());
        assertEquals(SupportStatus.PLANNED, releases.get(0).getOptimizationSupport());
        assertEquals(TestStatus.PASS, releases.get(0).getTestStatus());
        assertEquals(CompatibilityStatus.UNKNOWN, releases.get(1).getCompatibilityStatus());
        assertEquals(SupportStatus.PLANNED, releases.get(1).getOptimizationSupport());
        assertTrue(releases.get(1).getSupportedLoaders().isEmpty());
        assertEquals(1, fetcher.calls);
    }

    @Test
    void detailReadsTheOfficialJavaRequirementAndServerJar() throws Exception {
        FixtureFetcher fetcher = new FixtureFetcher();
        MinecraftVersionRegistry registry = new MinecraftVersionRegistry(INDEX, fetcher);
        registry.refresh();

        MinecraftRelease release = registry.getDetails("1.21.1");

        assertEquals(21, release.getRequiredJavaVersion().getAsInt());
        assertEquals("https://downloads.example.test/server.jar", release.getServerJar().get().toString());
        assertEquals(CompatibilityStatus.PARTIAL, release.getCompatibilityStatus());
        assertTrue(release.getSupportedLoaders().contains(LoaderId.FABRIC));
        assertEquals(2, fetcher.calls);
    }

    @Test
    void detailsRejectVersionsOutsideStableIndex() throws Exception {
        MinecraftVersionRegistry registry = new MinecraftVersionRegistry(INDEX, new FixtureFetcher(), VersionSupportCatalog.empty());
        registry.refresh();

        assertThrows(IllegalArgumentException.class, () -> registry.getDetails("1.21.2-snapshot-1"));
    }

    private static final class FixtureFetcher implements MetadataFetcher {
        private int calls;

        @Override
        public JsonObject fetch(URI uri) throws IOException {
            calls++;
            if (INDEX.equals(uri)) {
                return JsonParser.parseString("{\"versions\":["
                        + "{\"id\":\"1.21.1\",\"type\":\"release\",\"url\":\"https://metadata.example.test/1.21.1.json\",\"releaseTime\":\"2024-08-08T12:00:00Z\"},"
                        + "{\"id\":\"1.21.2-snapshot-1\",\"type\":\"snapshot\",\"url\":\"https://metadata.example.test/snapshot.json\",\"releaseTime\":\"2024-09-20T00:00:00Z\"},"
                        + "{\"id\":\"1.6.4\",\"type\":\"release\",\"url\":\"https://metadata.example.test/1.6.4.json\",\"releaseTime\":\"2013-09-19T15:52:37Z\"}]} ").getAsJsonObject();
            }
            if (uri.toString().endsWith("1.21.1.json")) {
                return JsonParser.parseString("{\"id\":\"1.21.1\",\"javaVersion\":{\"majorVersion\":21},"
                        + "\"downloads\":{\"server\":{\"url\":\"https://downloads.example.test/server.jar\"}}}").getAsJsonObject();
            }
            throw new IOException("Unexpected fixture request: " + uri);
        }
    }
}
