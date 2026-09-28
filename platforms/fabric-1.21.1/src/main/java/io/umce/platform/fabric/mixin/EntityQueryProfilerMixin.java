package io.umce.platform.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.umce.platform.fabric.optimization.EntityQueryProfiler;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;

/** Loaded only in an explicit diagnostic run; never present in passive or normal runs. */
@Mixin(SectionedEntityCache.class)
public abstract class EntityQueryProfilerMixin<T extends EntityLike> {
    @WrapMethod(method = "forEachInBox")
    private void umce$measureEntityQuery(Box box,
                                        LazyIterationConsumer<EntityTrackingSection<T>> consumer,
                                        Operation<Void> original) {
        EntityQueryProfiler.beginQuery();
        try {
            original.call(box, consumer);
        } finally {
            EntityQueryProfiler.endQuery();
        }
    }
}
