package io.umce.platform.fabric;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.compat.TestStatus;
import io.umce.api.platform.LoaderId;
import io.umce.api.platform.PlatformAdapter;
import io.umce.api.version.MinecraftRelease;
import io.umce.api.version.ReleaseType;
import io.umce.api.version.SupportStatus;
import net.minecraft.server.MinecraftServer;

final class FabricPlatformAdapter implements PlatformAdapter {
    private static final String MINECRAFT_VERSION = "26.3";
    private final MinecraftServer server;
    private final String loaderVersion;
    private final MinecraftRelease release;

    FabricPlatformAdapter(MinecraftServer server, String loaderVersion) {
        this.server = server;
        this.loaderVersion = loaderVersion;
        this.release = MinecraftRelease.builder(MINECRAFT_VERSION)
                .releaseType(ReleaseType.RELEASE)
                .requiredJavaVersion(25)
                .supportsLoader(LoaderId.FABRIC)
                .loaderVersion(LoaderId.FABRIC, loaderVersion)
                .adapterId("fabric-26.3")
                .optimizationSupport(SupportStatus.PARTIAL)
                .testStatus(TestStatus.PASS)
                .compatibilityStatus(CompatibilityStatus.PARTIAL)
                .build();
    }

    @Override public String getPlatformId() { return "fabric"; }
    @Override public String getPlatformVersion() { return "Fabric Loader " + loaderVersion; }
    @Override public LoaderId getLoaderId() { return LoaderId.FABRIC; }
    @Override public String getLoaderVersion() { return loaderVersion; }
    @Override public MinecraftRelease getMinecraftRelease() { return release; }
    @Override public boolean isMainThread() { return server.isSameThread(); }
    @Override public void executeOnMainThread(Runnable task) { server.execute(task); }
}
