package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.InsideWallLoopPatchRuntime;
import io.umce.platform.fabric.optimization.InsideWallScanGeometry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityInsideWallMixin {
    @Shadow public boolean noClip;
    @Shadow private EntityDimensions dimensions;

    @Shadow public abstract World getWorld();
    @Shadow public abstract Vec3d getEyePos();

    @Inject(method = "isInsideWall", at = @At("HEAD"), cancellable = true)
    private void umce$scanInsideWallWithoutStream(CallbackInfoReturnable<Boolean> callback) {
        if (!InsideWallLoopPatchRuntime.isEnabled()) return;
        if (noClip) {
            callback.setReturnValue(false);
            return;
        }

        Box box = InsideWallScanGeometry.eyeBox(getEyePos(), dimensions.width());
        VoxelShape boxShape = null;
        World world = getWorld();
        // Reuse vanilla's iterator to preserve coordinate order and inclusive floor boundaries.
        for (BlockPos pos : InsideWallScanGeometry.positions(box)) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !state.shouldSuffocate(world, pos)) continue;
            VoxelShape blockShape = state.getCollisionShape(world, pos).offset(pos.getX(), pos.getY(), pos.getZ());
            if (boxShape == null) boxShape = VoxelShapes.cuboid(box);
            if (VoxelShapes.matchesAnywhere(blockShape, boxShape, BooleanBiFunction.AND)) {
                callback.setReturnValue(true);
                return;
            }
        }
        callback.setReturnValue(false);
    }
}
