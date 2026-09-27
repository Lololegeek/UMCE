package io.umce.core.config;

import java.io.IOException;
import java.util.Locale;

public enum PatchPreference {
    AUTO,
    ON,
    OFF;

    public static PatchPreference parse(String value, String key) throws IOException {
        if (value == null) return AUTO;
        try {
            return PatchPreference.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid " + key + ": " + value + " (expected auto, on, or off)", exception);
        }
    }

    public String toConfigValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
