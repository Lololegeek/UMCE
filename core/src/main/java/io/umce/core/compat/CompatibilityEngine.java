package io.umce.core.compat;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.PatchEvaluation;

/** Conservative gate that combines a patch's local checks with environment evidence. */
public final class CompatibilityEngine {
    public PatchEvaluation evaluate(PatchEvaluation local, CompatibilityStatus compatibility,
                                    boolean explicitlyEnabled) {
        if (!explicitlyEnabled) {
            return new PatchEvaluation(PatchEvaluation.Status.DISABLED, "Patch is not explicitly enabled");
        }
        if (local == null || compatibility == null) {
            return PatchEvaluation.unknown("Required compatibility evidence is missing");
        }
        if (local.getStatus() != PatchEvaluation.Status.READY) return local;

        switch (compatibility) {
            case SUPPORTED:
            case SUPPORTED_WITH_PATCH:
                return PatchEvaluation.ready("Patch checks passed and compatibility is verified");
            case PARTIAL:
                return PatchEvaluation.unknown("Compatibility is partial; this patch remains disabled");
            case EXPERIMENTAL:
                return PatchEvaluation.unsafe("Experimental compatibility is not enabled by the safe gate");
            case UNSAFE:
            case INCOMPATIBLE:
                return PatchEvaluation.unsafe("Compatibility evidence marks this environment unsafe");
            case UNKNOWN:
            default:
                return PatchEvaluation.unknown("Compatibility is unknown; unknown is not treated as safe");
        }
    }
}
