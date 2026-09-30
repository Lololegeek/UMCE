package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

public final class InsideWallLoopPatch implements OptimizationPatch {
    public static final String ID = "inside-wall-loop";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Experimental eye-centered suffocation scan", OptimizationCategory.CPU, PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED, "SAFE uses the original suffocation scan");
        }
        if (!"true".equals(context.getSettings().get("inside-wall-hook.available"))) {
            String blockers = context.getSettings().get("inventory-scan.blockers");
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    blockers != null && !blockers.isEmpty() ? "Unverified mods prevent suffocation hooks: " + blockers
                            : "Select this experimental patch before startup to load its Mixin");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED, "Only Fabric 1.21.1 is implemented");
        }
        return PatchEvaluation.ready("Uses vanilla eye geometry, block order and shape checks without Stream setup");
    }

    @Override
    public PatchHandle apply(PatchContext context) {
        InsideWallLoopPatchRuntime.setEnabled(true);
        return () -> InsideWallLoopPatchRuntime.setEnabled(false);
    }
}
