package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.EntityQueryPatchRuntime;
import io.umce.platform.fabric.optimization.EntityQueryProfiler;
import io.umce.platform.fabric.optimization.PackedSectionCoordinateOrder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionedEntityCache.class)
public abstract class SectionedEntityCacheMixin<T extends EntityLike> {
    private static final long MAX_DIRECT_SECTION_PROBES = 64L;

    @Shadow @Final private Long2ObjectMap<EntityTrackingSection<T>> trackingSections;

    @Inject(method = "forEachInBox", at = @At("HEAD"), cancellable = true)
    private void umce$probeSmallBoxes(Box box,
                                     LazyIterationConsumer<EntityTrackingSection<T>> consumer,
                                     CallbackInfo callback) {
        if (!EntityQueryPatchRuntime.isSmallBoxSectionProbeEnabled()) return;

        int minX = ChunkSectionPos.getSectionCoord(box.minX - 2.0);
        int minY = ChunkSectionPos.getSectionCoord(box.minY - 4.0);
        int minZ = ChunkSectionPos.getSectionCoord(box.minZ - 2.0);
        int maxX = ChunkSectionPos.getSectionCoord(box.maxX + 2.0);
        int maxY = ChunkSectionPos.getSectionCoord(box.maxY);
        int maxZ = ChunkSectionPos.getSectionCoord(box.maxZ + 2.0);

        long xCount = (long) maxX - minX + 1L;
        long yCount = (long) maxY - minY + 1L;
        long zCount = (long) maxZ - minZ + 1L;
        if (xCount <= 0L || yCount <= 0L || zCount <= 0L
                || xCount > MAX_DIRECT_SECTION_PROBES
                || yCount > MAX_DIRECT_SECTION_PROBES / xCount
                || zCount > MAX_DIRECT_SECTION_PROBES / (xCount * yCount)) {
            if (EntityQueryPatchRuntime.isProfilingEnabled()) {
                EntityQueryProfiler.markFallback();
            }
            return;
        }

        for (long xOffset = 0; xOffset < xCount; xOffset++) {
            int x = (int) (minX + xOffset);
            for (long zOffset = 0; zOffset < zCount; zOffset++) {
                int z = PackedSectionCoordinateOrder.valueAt(minZ, maxZ, zOffset);
                for (long yOffset = 0; yOffset < yCount; yOffset++) {
                    int y = PackedSectionCoordinateOrder.valueAt(minY, maxY, yOffset);
                    EntityTrackingSection<T> section = trackingSections.get(ChunkSectionPos.asLong(x, y, z));
                    if (section == null || section.isEmpty()) continue;
                    EntityTrackingStatus status = section.getStatus();
                    if (!status.shouldTrack()) continue;
                    if (consumer.accept(section).shouldAbort()) {
                        callback.cancel();
                        return;
                    }
                }
            }
        }
        if (EntityQueryPatchRuntime.isProfilingEnabled()) {
            EntityQueryProfiler.markAccelerated(xCount * yCount * zCount);
        }
        callback.cancel();
    }
}
