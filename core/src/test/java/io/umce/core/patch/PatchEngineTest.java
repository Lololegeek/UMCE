package io.umce.core.patch;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.OptimizationCategory;
import io.umce.api.patch.OptimizationPatch;
import io.umce.api.patch.PatchContext;
import io.umce.api.patch.PatchDescriptor;
import io.umce.api.patch.PatchEvaluation;
import io.umce.api.patch.PatchHandle;
import io.umce.api.patch.PatchRisk;
import io.umce.api.platform.LoaderId;
import io.umce.api.platform.PlatformAdapter;
import io.umce.api.version.MinecraftRelease;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PatchEngineTest {
    @Test
    void activationRequiresExplicitEnableAndReadyCompatibilityThenCanBeReverted() {
        AtomicInteger effects = new AtomicInteger();
        PatchEngine engine = new PatchEngine();
        engine.register(patch("test:reversible", PatchEvaluation.ready("hook verified"), effects, false));

        PatchOperationResult ignoredDefault = engine.enable("test:reversible", context(), CompatibilityStatus.SUPPORTED, false);
        assertEquals(PatchState.DISABLED, ignoredDefault.getState());
        assertEquals(0, effects.get());

        PatchOperationResult enabled = engine.enable("test:reversible", context(), CompatibilityStatus.SUPPORTED, true);
        assertEquals(PatchState.ENABLED, enabled.getState());
        assertEquals(1, effects.get());

        PatchOperationResult disabled = engine.disable("test:reversible");
        assertEquals(PatchState.DISABLED, disabled.getState());
        assertEquals(0, effects.get());
    }

    @Test
    void unknownCompatibilityKeepsPatchDisabled() {
        AtomicInteger effects = new AtomicInteger();
        PatchEngine engine = new PatchEngine();
        engine.register(patch("test:unknown", PatchEvaluation.ready("local checks passed"), effects, true));

        PatchOperationResult result = engine.enable("test:unknown", context(), CompatibilityStatus.UNKNOWN, true);

        assertEquals(PatchState.DISABLED, result.getState());
        assertEquals(PatchEvaluation.Status.UNKNOWN, result.getEvaluation().getStatus());
        assertEquals(0, effects.get());
    }

    @Test
    void onePatchFailureDoesNotPreventAnotherPatchFromActivating() {
        PatchEngine engine = new PatchEngine();
        engine.register(new OptimizationPatch() {
            @Override public PatchDescriptor getDescriptor() { return descriptor("test:broken", false); }
            @Override public PatchEvaluation evaluate(PatchContext context) { return PatchEvaluation.ready("verified"); }
            @Override public PatchHandle apply(PatchContext context) throws Exception { throw new IllegalStateException("simulated patch failure"); }
        });
        AtomicInteger effects = new AtomicInteger();
        engine.register(patch("test:healthy", PatchEvaluation.ready("verified"), effects, false));

        assertEquals(PatchState.FAILED, engine.enable("test:broken", context(), CompatibilityStatus.SUPPORTED, true).getState());
        assertEquals(PatchState.ENABLED, engine.enable("test:healthy", context(), CompatibilityStatus.SUPPORTED, true).getState());
        assertEquals(1, effects.get());
    }

    private static OptimizationPatch patch(String id, PatchEvaluation evaluation, AtomicInteger effects, boolean enabledByDefault) {
        return new OptimizationPatch() {
            @Override public PatchDescriptor getDescriptor() { return descriptor(id, enabledByDefault); }
            @Override public PatchEvaluation evaluate(PatchContext context) { return evaluation; }
            @Override public PatchHandle apply(PatchContext context) {
                effects.incrementAndGet();
                return effects::decrementAndGet;
            }
        };
    }

    private static PatchDescriptor descriptor(String id, boolean enabledByDefault) {
        return new PatchDescriptor(id, id, OptimizationCategory.CPU, PatchRisk.LOW, enabledByDefault);
    }

    private static PatchContext context() {
        PlatformAdapter adapter = new PlatformAdapter() {
            private final MinecraftRelease release = MinecraftRelease.builder("26.3").build();
            @Override public String getPlatformId() { return "test"; }
            @Override public String getPlatformVersion() { return "test"; }
            @Override public LoaderId getLoaderId() { return LoaderId.OTHER; }
            @Override public String getLoaderVersion() { return "test"; }
            @Override public MinecraftRelease getMinecraftRelease() { return release; }
            @Override public boolean isMainThread() { return true; }
            @Override public void executeOnMainThread(Runnable task) { task.run(); }
        };
        return new PatchContext(adapter, Collections.<String, String>emptyMap());
    }
}
