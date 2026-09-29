package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.PoiCandidateCollectionPatchRuntime;
import io.umce.platform.fabric.optimization.SequentialStreamCollector;
import net.minecraft.entity.ai.brain.task.FindPointOfInterestTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Collector;
import java.util.stream.Stream;

@Mixin(FindPointOfInterestTask.class)
public abstract class FindPointOfInterestTaskMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "method_46885", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;",
                    remap = false))
    private static Object umce$collectPoiCandidates(Stream<?> stream, Collector<?, ?, ?> collector) {
        if (!PoiCandidateCollectionPatchRuntime.isEnabled()) {
            return stream.collect((Collector) collector);
        }
        return SequentialStreamCollector.collect((Stream) stream, (Collector) collector);
    }
}
