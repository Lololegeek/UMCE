package io.umce.platform.fabric.optimization;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Exact geometry and enumeration used by vanilla Entity.isInsideWall in 1.21.1. */
public final class InsideWallScanGeometry {
    private InsideWallScanGeometry() { }
    public static Box eyeBox(Vec3d eyePosition, float entityWidth) {
        float width = entityWidth * 0.8f;
        return Box.of(eyePosition, width, 1.0E-6, width);
    }
    public static Iterable<BlockPos> positions(Box box) {
        return BlockPos.iterate(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
                MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ));
    }
}
