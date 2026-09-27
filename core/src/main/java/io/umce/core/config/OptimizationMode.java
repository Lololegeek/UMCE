package io.umce.core.config;

import java.io.IOException;
import java.util.Locale;

public enum OptimizationMode {
    SAFE,
    AUTO,
    BALANCED,
    PERFORMANCE,
    MEMORY,
    MANUAL;

    public static OptimizationMode parse(String value) throws IOException {
        if (value == null) throw new IOException("Missing optimization.mode");
        try {
            return OptimizationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid optimization.mode: " + value
                    + " (expected safe, auto, balanced, performance, memory, or manual)", exception);
        }
    }

    public String toConfigValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
