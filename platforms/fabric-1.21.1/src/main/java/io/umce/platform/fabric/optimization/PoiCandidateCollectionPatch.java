package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

public final class PoiCandidateCollectionPatch implements OptimizationPatch {
    public static final String ID = "poi-candidate-collection";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Sequential POI collector experiment (no screen gain)", OptimizationCategory.ENTITIES, PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED, "SAFE mode uses the vanilla POI collector");
        }
        if (!"true".equals(context.getSettings().get("poi-collection-hook.available"))) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "The startup omitted this experimental Mixin; configure it before server startup");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "This patch is verified only for Fabric on Minecraft 1.21.1");
        }
        return PatchEvaluation.ready("Preserves the POI stream and Collector while using a sequential terminal loop");
    }

    @Override
    public PatchHandle apply(PatchContext context) {
        PoiCandidateCollectionPatchRuntime.setEnabled(true);
        return () -> PoiCandidateCollectionPatchRuntime.setEnabled(false);
    }
}
