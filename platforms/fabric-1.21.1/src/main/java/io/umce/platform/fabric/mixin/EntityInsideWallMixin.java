package io.umce.platform.fabric.mixin;

import io.umce.platform.fabric.optimization.InsideWallLoopPatchRuntime;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityInsideWallMixin {
    @Shadow public boolean noClip;
    @Shadow @Final private EntityDimensions dimensions;

    @Shadow public abstract World getWorld();
    @Shadow public abstract Box getBoundingBox();

    @Inject(method = "isInsideWall", at = @At("HEAD"), cancellable = true)
    private void umce$scanInsideWallWithoutStream(CallbackInfoReturnable<Boolean> callback) {
        if (!InsideWallLoopPatchRuntime.isEnabled()) return;
        if (noClip) {
            callback.setReturnValue(false);
            return;
        }

        float expansion = dimensions.width() * 0.8f;
        Box box = getBoundingBox().expand(expansion, 1.0E-6, expansion);
        VoxelShape boxShape = VoxelShapes.cuboid(box);
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);
        World world = getWorld();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // BlockPos.stream(Box) visits x fastest, then z, then y, reusing a Mutable BlockPos.
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !state.shouldSuffocate(world, pos)) continue;
                    if (VoxelShapes.matchesAnywhere(
                            state.getCollisionShape(world, pos).offset(x, y, z),
                            boxShape,
                            BooleanBiFunction.AND)) {
                        callback.setReturnValue(true);
                        return;
                    }
                }
            }
        }
        callback.setReturnValue(false);
    }
}
