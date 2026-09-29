package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

public final class BrainTaskLaunchCachePatch implements OptimizationPatch {
    public static final String ID = "brain-task-launch-cache";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Experimental Brain task-list cache (screen regressed)", OptimizationCategory.ENTITIES, PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED, "SAFE mode uses vanilla Brain task traversal");
        }
        if (!"true".equals(context.getSettings().get("brain-task-hook.available"))) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "The startup omitted this experimental Mixin or a known Brain-transforming mod is present");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    "This patch is verified only for Fabric on Minecraft 1.21.1");
        }
        return PatchEvaluation.ready("Caches active Brain task order until task or activity configuration changes");
    }

    @Override
    public PatchHandle apply(PatchContext context) {
        BrainTaskLaunchCachePatchRuntime.setEnabled(true);
        return () -> BrainTaskLaunchCachePatchRuntime.setEnabled(false);
    }
}
