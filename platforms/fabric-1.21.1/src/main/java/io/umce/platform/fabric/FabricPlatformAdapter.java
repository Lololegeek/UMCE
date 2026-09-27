package io.umce.platform.fabric;

import io.umce.api.platform.LoaderId;
import io.umce.api.platform.PlatformAdapter;
import io.umce.api.version.MinecraftRelease;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class FabricPlatformAdapter implements PlatformAdapter {
    private final String loaderVersion;
    private volatile MinecraftServer server;

    public FabricPlatformAdapter() {
        this.loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Override public String getPlatformId() { return "fabric-1.21.1"; }
    @Override public String getPlatformVersion() { return loaderVersion; }
    @Override public LoaderId getLoaderId() { return LoaderId.FABRIC; }
    @Override public String getLoaderVersion() { return loaderVersion; }
    @Override public MinecraftRelease getMinecraftRelease() {
        return MinecraftRelease.builder("1.21.1")
                .requiredJavaVersion(21)
                .adapterId("fabric-1.21.1")
                .build();
    }
    @Override public boolean isMainThread() {
        MinecraftServer current = server;
        return current != null && current.isOnThread();
    }
    @Override public void executeOnMainThread(Runnable task) {
        MinecraftServer current = server;
        if (current == null) throw new IllegalStateException("Minecraft server has not started");
        current.execute(task);
    }
}
