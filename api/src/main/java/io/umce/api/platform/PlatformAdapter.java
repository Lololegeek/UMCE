package io.umce.api.platform;

import io.umce.api.version.MinecraftRelease;

/** Bridge implemented by a loader/server-specific artifact. */
public interface PlatformAdapter {
    String getPlatformId();
    String getPlatformVersion();
    LoaderId getLoaderId();
    String getLoaderVersion();
    MinecraftRelease getMinecraftRelease();
    boolean isMainThread();
    void executeOnMainThread(Runnable task);
}
