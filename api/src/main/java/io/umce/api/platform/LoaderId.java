package io.umce.api.platform;

import java.util.Locale;

public enum LoaderId {
    VANILLA("vanilla"),
    FML("fml"),
    FORGE("forge"),
    FABRIC("fabric"),
    QUILT("quilt"),
    NEOFORGE("neoforge"),
    ORNITHE("ornithe"),
    BUKKIT("bukkit"),
    PAPER("paper"),
    PURPUR("purpur"),
    FOLIA("folia"),
    SPONGE("sponge"),
    OTHER("other");

    private final String id;

    LoaderId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static LoaderId fromId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("loader id must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (LoaderId loader : values()) {
            if (loader.id.equals(normalized) || loader.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return loader;
            }
        }
        return OTHER;
    }
}
