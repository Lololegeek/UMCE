package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.InsideWallLoopPatchRuntime;
import io.umce.platform.fabric.optimization.InsideWallScanGeometry;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class EntityInsideWallMixin {
    @Shadow public boolean noClip;
    @Shadow private EntityDimensions dimensions;

    @Shadow public abstract World getWorld();
    @Shadow public abstract Vec3d getEyePos();

    @WrapMethod(method = "isInsideWall")
    private boolean umce$scanInsideWallWithoutStream(Operation<Boolean> original) {
        if (!InsideWallLoopPatchRuntime.isEnabled()) return original.call();
        if (noClip) return false;
        Box box = InsideWallScanGeometry.eyeBox(getEyePos(), dimensions.width());
        return InsideWallScanGeometry.intersectsSuffocatingBlock(getWorld(), box);
    }
}
