package io.umce.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path directory;

    @Test
    void createsConservativeDefaultsAndReloadsOperatorChanges() throws Exception {
        Path file = directory.resolve("umce.properties");
        ConfigStore store = new ConfigStore(file, 8);

        UmceConfig defaults = store.loadOrCreate();
        assertEquals(7, defaults.getCpuWorkers());
        assertFalse(defaults.isGpuEnabled());
        assertFalse(defaults.isDashboardEnabled());
        assertEquals("127.0.0.1", defaults.getDashboardBind());

        Files.write(file, "profile=performance\ncpu.workers=4\n".getBytes(StandardCharsets.ISO_8859_1));
        UmceConfig reloaded = store.reload();
        assertEquals("performance", reloaded.getProfile());
        assertEquals(4, reloaded.getCpuWorkers());
    }

    @Test
    void rejectsRemoteDashboardBindsAndUnknownKeys() throws Exception {
        Path file = directory.resolve("umce.properties");
        Files.write(file, "profile=balanced\ndashboard.bind=0.0.0.0\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(IOException.class, () -> new ConfigStore(file, 4).reload());

        Files.write(file, "profile=balanced\noptimizations.experimental=true\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(IOException.class, () -> new ConfigStore(file, 4).reload());
    }

    @Test
    void rejectsInvalidWorkerLimits() throws Exception {
        Path file = directory.resolve("umce.properties");
        Files.write(file, "profile=balanced\ncpu.workers=0\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(IOException.class, () -> new ConfigStore(file, 4).reload());
    }
}
