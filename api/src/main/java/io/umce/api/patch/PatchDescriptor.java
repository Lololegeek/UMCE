package io.umce.api.patch;

public final class PatchDescriptor {
    private final String id;
    private final String displayName;
    private final OptimizationCategory category;
    private final PatchRisk risk;
    private final boolean enabledByDefault;
    private final boolean autoEligible;

    public PatchDescriptor(String id, String displayName, OptimizationCategory category,
                           PatchRisk risk, boolean enabledByDefault) {
        this(id, displayName, category, risk, enabledByDefault, false);
    }

    public PatchDescriptor(String id, String displayName, OptimizationCategory category,
                           PatchRisk risk, boolean enabledByDefault, boolean autoEligible) {
        this.id = text(id, "id");
        this.displayName = text(displayName, "displayName");
        this.category = require(category, "category");
        this.risk = require(risk, "risk");
        this.enabledByDefault = enabledByDefault;
        this.autoEligible = autoEligible;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public OptimizationCategory getCategory() { return category; }
    public PatchRisk getRisk() { return risk; }
    public boolean isEnabledByDefault() { return enabledByDefault; }
    public boolean isAutoEligible() { return autoEligible; }

    private static String text(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        return value;
    }
}
