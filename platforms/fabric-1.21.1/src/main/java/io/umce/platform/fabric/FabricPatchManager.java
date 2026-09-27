package io.umce.platform.fabric;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.PatchContext;
import io.umce.core.patch.PatchEngine;
import io.umce.core.patch.PatchOperationResult;
import io.umce.core.patch.PatchState;
import io.umce.platform.fabric.optimization.EntityQueryPatchRuntime;
import io.umce.platform.fabric.optimization.SmallBoxSectionProbePatch;

import java.util.Collections;
import java.util.Locale;

public final class FabricPatchManager implements AutoCloseable {
    private final PatchEngine engine = new PatchEngine();
    private final PatchContext context;
    private final String mode;

    public FabricPatchManager(FabricPlatformAdapter adapter) {
        String requestedMode = System.getProperty("umce.mode", "safe").trim().toLowerCase(Locale.ROOT);
        this.mode = "optimized".equals(requestedMode) ? "optimized" : "safe";
        this.context = new PatchContext(adapter, Collections.singletonMap("mode", mode));
        engine.register(new SmallBoxSectionProbePatch());
        if (Boolean.getBoolean("umce.patch.small-box-section-probe.enabled")) {
            PatchOperationResult result = enable(SmallBoxSectionProbePatch.ID);
            if (result.getState() != PatchState.ENABLED) {
                org.slf4j.LoggerFactory.getLogger("UMCE").warn("Patch {} stayed disabled: {}",
                        result.getPatchId(), result.getEvaluation().getReason());
            }
        }
    }

    public String getMode() { return mode; }

    public PatchOperationResult enable(String patchId) {
        return engine.enable(patchId, context, CompatibilityStatus.SUPPORTED, true);
    }

    public PatchOperationResult disable(String patchId) {
        return engine.disable(patchId);
    }

    public boolean isSmallBoxSectionProbeEnabled() {
        return EntityQueryPatchRuntime.isSmallBoxSectionProbeEnabled();
    }

    @Override public void close() { engine.close(); }
}
