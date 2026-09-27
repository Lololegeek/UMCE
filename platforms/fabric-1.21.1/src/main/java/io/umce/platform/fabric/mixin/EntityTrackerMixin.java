package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.EntityTrackingPatchRuntime;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class EntityTrackerMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private int maxDistance;
    @Shadow private int adjustTrackingDistance(int distance) {
        throw new AssertionError("Shadow method replaced by Mixin");
    }

    @Inject(method = "getMaxTrackDistance", at = @At("HEAD"), cancellable = true)
    private void umce$skipEmptyPassengerTraversal(CallbackInfoReturnable<Integer> callback) {
        if (EntityTrackingPatchRuntime.isEmptyPassengerTrackDistanceEnabled()
                && !entity.hasPassengers()) {
            callback.setReturnValue(adjustTrackingDistance(maxDistance));
        }
    }
}
