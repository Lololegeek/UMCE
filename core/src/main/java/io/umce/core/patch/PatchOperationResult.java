package io.umce.core.patch;

import io.umce.api.patch.PatchEvaluation;

public final class PatchOperationResult {
    private final String patchId;
    private final PatchState state;
    private final PatchEvaluation evaluation;

    PatchOperationResult(String patchId, PatchState state, PatchEvaluation evaluation) {
        this.patchId = patchId;
        this.state = state;
        this.evaluation = evaluation;
    }

    public String getPatchId() { return patchId; }
    public PatchState getState() { return state; }
    public PatchEvaluation getEvaluation() { return evaluation; }
    public boolean isEnabled() { return state == PatchState.ENABLED; }
}
