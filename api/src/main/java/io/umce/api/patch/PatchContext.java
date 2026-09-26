package io.umce.api.patch;

import io.umce.api.platform.PlatformAdapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PatchContext {
    private final PlatformAdapter platform;
    private final Map<String, String> settings;

    public PatchContext(PlatformAdapter platform, Map<String, String> settings) {
        if (platform == null) throw new IllegalArgumentException("platform must not be null");
        this.platform = platform;
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<String, String>(settings));
    }

    public PlatformAdapter getPlatform() { return platform; }
    public Map<String, String> getSettings() { return settings; }
}
