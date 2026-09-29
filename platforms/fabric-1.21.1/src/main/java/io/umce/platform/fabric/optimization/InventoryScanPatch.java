package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

/** Two independently registered experiments sharing compatibility and lifecycle code. */
public final class InventoryScanPatch implements OptimizationPatch {
    public enum Kind {
        CONTAINER_EMPTY("container-empty-scan", "container-empty-hook.available"),
        HOPPER_FULL("hopper-full-scan", "hopper-full-hook.available");
        public final String id;
        private final String availabilityKey;
        Kind(String id, String availabilityKey) { this.id = id; this.availabilityKey = availabilityKey; }
    }
    private final Kind kind;
    private final PatchDescriptor descriptor;

    public InventoryScanPatch(Kind kind) {
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
        descriptor = new PatchDescriptor(kind.id, "Experimental indexed inventory scan (unmeasured)",
                OptimizationCategory.CPU, PatchRisk.MEDIUM, false, false);
    }

    @Override public PatchDescriptor getDescriptor() { return descriptor; }
    @Override public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED, "SAFE uses vanilla inventory scans");
        }
        if (!"true".equals(context.getSettings().get(kind.availabilityKey))) {
            String blockers = context.getSettings().get("inventory-scan.blockers");
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    blockers != null && !blockers.isEmpty() ? "Unverified mods prevent inventory hooks: " + blockers
                            : "Select this manual patch before startup to load its Mixin");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED, "Only Fabric 1.21.1 is implemented");
        }
        return PatchEvaluation.ready("Scans current vanilla stacks in order without an iterator or state cache");
    }
    @Override public PatchHandle apply(PatchContext context) {
        setEnabled(true);
        return () -> setEnabled(false);
    }
    private void setEnabled(boolean enabled) {
        if (kind == Kind.CONTAINER_EMPTY) InventoryScanPatchRuntime.setContainerEmptyEnabled(enabled);
        else InventoryScanPatchRuntime.setHopperFullEnabled(enabled);
    }
}
