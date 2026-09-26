package io.umce.core.compat;

import io.umce.api.compat.CompatibilityStatus;
import io.umce.api.patch.PatchEvaluation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityEngineTest {
    private final CompatibilityEngine engine = new CompatibilityEngine();

    @Test
    void unknownCompatibilityCannotPassEvenWhenPatchCheckIsReady() {
        PatchEvaluation result = engine.evaluate(PatchEvaluation.ready("hook matched"),
                CompatibilityStatus.UNKNOWN, true);

        assertEquals(PatchEvaluation.Status.UNKNOWN, result.getStatus());
    }

    @Test
    void supportMustBeVerifiedAndExplicitlyEnabled() {
        assertEquals(PatchEvaluation.Status.READY,
                engine.evaluate(PatchEvaluation.ready("verified"), CompatibilityStatus.SUPPORTED, true).getStatus());
        assertEquals(PatchEvaluation.Status.DISABLED,
                engine.evaluate(PatchEvaluation.ready("verified"), CompatibilityStatus.SUPPORTED, false).getStatus());
    }

    @Test
    void unsafeCompatibilityStopsAnOtherwiseReadyPatch() {
        assertEquals(PatchEvaluation.Status.UNSAFE,
                engine.evaluate(PatchEvaluation.ready("hook matched"), CompatibilityStatus.INCOMPATIBLE, true).getStatus());
    }
}
