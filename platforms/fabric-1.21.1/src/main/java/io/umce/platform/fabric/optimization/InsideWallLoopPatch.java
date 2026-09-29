package io.umce.platform.fabric.optimization;

import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;

public final class InsideWallLoopPatch implements OptimizationPatch {
    public static final String ID = "inside-wall-loop";
    private static final PatchDescriptor DESCRIPTOR = new PatchDescriptor(ID,
            "Inside-wall Stream scan experiment (rejected)", OptimizationCategory.CPU, PatchRisk.MEDIUM, false, false);

    @Override public PatchDescriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public PatchEvaluation evaluate(PatchContext context) {
        return new PatchEvaluation(PatchEvaluation.Status.DISABLED,
                "Rejected: vanilla suffocation outcomes diverged; hook locked off pending a behavior-equivalent rewrite");
    }

    @Override
    public PatchHandle apply(PatchContext context) {
        InsideWallLoopPatchRuntime.setEnabled(true);
        return () -> InsideWallLoopPatchRuntime.setEnabled(false);
    }
}
