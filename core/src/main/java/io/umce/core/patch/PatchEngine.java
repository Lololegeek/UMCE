package io.umce.core.patch;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchDescriptor;
import io.umce.core.compat.CompatibilityEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reversible, per-patch activation with conservative compatibility gating. */
public final class PatchEngine implements AutoCloseable {
    private final CompatibilityEngine compatibilityEngine;
    private final Map<String, OptimizationPatch> patches = new HashMap<String, OptimizationPatch>();
    private final Map<String, PatchHandle> activeHandles = new HashMap<String, PatchHandle>();
    private final Map<String, PatchOperationResult> lastResults = new HashMap<String, PatchOperationResult>();

    public PatchEngine() {
        this(new CompatibilityEngine());
    }

    public PatchEngine(CompatibilityEngine compatibilityEngine) {
        if (compatibilityEngine == null) throw new IllegalArgumentException("compatibilityEngine must not be null");
        this.compatibilityEngine = compatibilityEngine;
    }

    public synchronized void register(OptimizationPatch patch) {
        if (patch == null || patch.getDescriptor() == null) throw new IllegalArgumentException("patch and descriptor must not be null");
        String id = patch.getDescriptor().getId();
        if (patches.putIfAbsent(id, patch) != null) throw new IllegalArgumentException("Duplicate patch id: " + id);
        lastResults.put(id, new PatchOperationResult(id, PatchState.DISABLED,
                new PatchEvaluation(PatchEvaluation.Status.DISABLED, "Patch registered; explicit activation is required")));
    }

    public synchronized PatchOperationResult enable(String patchId, PatchContext context,
                                                     CompatibilityStatus compatibility,
                                                     boolean explicitlyEnabled) {
        OptimizationPatch patch = requirePatch(patchId);
        if (activeHandles.containsKey(patchId)) return lastResults.get(patchId);
        if (!explicitlyEnabled) {
            return save(patchId, PatchState.DISABLED,
                    new PatchEvaluation(PatchEvaluation.Status.DISABLED, "Patch is not explicitly enabled"));
        }

        PatchEvaluation local;
        try {
            local = patch.evaluate(context);
        } catch (RuntimeException exception) {
            return save(patchId, PatchState.FAILED, PatchEvaluation.unsafe(
                    "Patch readiness check failed: " + describe(exception)));
        }
        PatchEvaluation decision = compatibilityEngine.evaluate(local, compatibility, true);
        if (decision == null || !decision.isReady()) {
            if (decision == null) decision = PatchEvaluation.unknown("Compatibility gate returned no decision");
            return save(patchId, PatchState.DISABLED, decision);
        }

        try {
            PatchHandle handle = patch.apply(context);
            if (handle == null) throw new IllegalStateException("Patch returned no reversible handle");
            activeHandles.put(patchId, handle);
            return save(patchId, PatchState.ENABLED, decision);
        } catch (Exception exception) {
            return save(patchId, PatchState.FAILED, PatchEvaluation.unsafe(
                    "Patch activation failed: " + describe(exception)));
        }
    }

    public synchronized PatchOperationResult disable(String patchId) {
        requirePatch(patchId);
        PatchHandle handle = activeHandles.remove(patchId);
        if (handle == null) {
            return save(patchId, PatchState.DISABLED,
                    new PatchEvaluation(PatchEvaluation.Status.DISABLED, "Patch is not active"));
        }
        try {
            handle.close();
            return save(patchId, PatchState.DISABLED,
                    new PatchEvaluation(PatchEvaluation.Status.DISABLED, "Patch was reverted"));
        } catch (Exception exception) {
            return save(patchId, PatchState.FAILED, PatchEvaluation.unsafe(
                    "Patch rollback failed: " + describe(exception)));
        }
    }

    public synchronized PatchOperationResult getLastResult(String patchId) {
        requirePatch(patchId);
        return lastResults.get(patchId);
    }

    @Override
    public synchronized void close() {
        List<String> active = new ArrayList<String>(activeHandles.keySet());
        for (String patchId : active) disable(patchId);
    }

    private OptimizationPatch requirePatch(String patchId) {
        OptimizationPatch patch = patches.get(patchId);
        if (patch == null) throw new IllegalArgumentException("Unknown patch: " + patchId);
        return patch;
    }

    private PatchOperationResult save(String patchId, PatchState state, PatchEvaluation evaluation) {
        PatchOperationResult result = new PatchOperationResult(patchId, state, evaluation);
        lastResults.put(patchId, result);
        return result;
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.trim().isEmpty() ? "" : ": " + message);
    }
}
