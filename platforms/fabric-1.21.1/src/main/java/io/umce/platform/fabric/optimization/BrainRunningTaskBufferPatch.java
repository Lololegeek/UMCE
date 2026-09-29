package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;

/** Allocation candidate: never selected automatically until measured. */
public final class BrainRunningTaskBufferPatch implements OptimizationPatch {
    public static final String ID = "brain-running-task-buffer";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Experimental reusable running-task snapshot (unmeasured)",
            OptimizationCategory.ENTITIES, PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override public PatchEvaluation evaluate(PatchContext context) {
        if (!"optimized".equalsIgnoreCase(context.getSettings().get("mode"))) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED, "SAFE uses vanilla running-task snapshots");
        }
        if (!"true".equals(context.getSettings().get("brain-running-buffer-hook.available"))) {
            String blockers = context.getSettings().get("brain-running-buffer.blockers");
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED,
                    blockers != null && !blockers.isEmpty() ? "Unverified mods prevent snapshot reuse: " + blockers
                            : "Restart with this manual patch selected to load its Mixin");
        }
        if (!"fabric-1.21.1".equals(context.getPlatform().getPlatformId())
                || context.getPlatform().getLoaderId() != LoaderId.FABRIC
                || !"1.21.1".equals(context.getPlatform().getMinecraftRelease().getId())) {
            return new PatchEvaluation(PatchEvaluation.Status.UNSUPPORTED, "Only the Fabric 1.21.1 adapter is implemented");
        }
        return PatchEvaluation.ready("Rebuilds the running-task snapshot every tick with bounded reusable storage");
    }

    @Override public PatchHandle apply(PatchContext context) {
        BrainRunningTaskBufferPatchRuntime.setEnabled(true);
        return () -> BrainRunningTaskBufferPatchRuntime.setEnabled(false);
    }
}
