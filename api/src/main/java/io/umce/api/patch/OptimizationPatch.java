package io.umce.api.patch;

public interface OptimizationPatch {
    PatchDescriptor getDescriptor();

    /** Unknown conditions must return UNKNOWN or UNSAFE, never READY. */
    PatchEvaluation evaluate(PatchContext context);

    /** Called only after an explicit enable request and a READY evaluation. */
    PatchHandle apply(PatchContext context) throws Exception;
}
