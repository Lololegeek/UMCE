package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

/** Experimental manual-only candidate; the initial 10-player ablation regressed. */
public final class EmptyPassengerTrackDistancePatch implements OptimizationPatch {
    public static final String ID = "empty-passenger-track-distance";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Skip empty passenger traversal in entity tracking", OptimizationCategory.NETWORK,
            PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED,
                    "SAFE mode keeps vanilla passenger traversal active");
        }
        if (!"true".equals(context.getSettings().get("passenger-tracking-hook.available"))) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "The passive startup omitted this Mixin; configure the patch before starting or restart the server");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "This patch is verified only for Fabric on Minecraft 1.21.1");
        }
        return PatchEvaluation.ready("Experimental passenger fast path for Fabric 1.21.1");
    }

    @Override
    public PatchHandle apply(PatchContext context) {
        EntityTrackingPatchRuntime.setEmptyPassengerTrackDistanceEnabled(true);
        return () -> EntityTrackingPatchRuntime.setEmptyPassengerTrackDistanceEnabled(false);
    }
}
