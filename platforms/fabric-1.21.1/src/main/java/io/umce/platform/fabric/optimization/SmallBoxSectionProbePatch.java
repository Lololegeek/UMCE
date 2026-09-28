package io.umce.platform.fabric.optimization;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

public final class SmallBoxSectionProbePatch implements OptimizationPatch {
    public static final String ID = "small-box-section-probe";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Small entity-query section probes", OptimizationCategory.ENTITIES, PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED,
                    "SAFE mode keeps the vanilla entity section scan active");
        }
        if (!"true".equals(context.getSettings().get("entity-query-hook.available"))) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "The passive startup omitted this Mixin; configure the patch before starting or restart the server");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "This patch is verified only for Fabric on Minecraft 1.21.1");
        }
        return PatchEvaluation.ready("Small-box packed section traversal is available on Fabric 1.21.1");
    }

    @Override
    public PatchHandle apply(PatchContext context) {
        EntityQueryPatchRuntime.setSmallBoxSectionProbeEnabled(true);
        return () -> EntityQueryPatchRuntime.setSmallBoxSectionProbeEnabled(false);
    }
}
