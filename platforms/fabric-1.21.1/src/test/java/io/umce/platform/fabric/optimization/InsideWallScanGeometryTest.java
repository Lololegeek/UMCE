package io.umce.platform.fabric.optimization;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InsideWallScanGeometryTest {
    @Test
    void eyeCenteredBoxExcludesGroundWhichRejectedBodyExpansionIncluded() {
        Vec3d eye = new Vec3d(0.5, 1.62, 0.5);
        float width = 0.6f;
        Box actual = InsideWallScanGeometry.eyeBox(eye, width);
        Box vanilla = Box.of(eye, width * 0.8f, 1.0E-6, width * 0.8f);
        assertEquals(vanilla, actual);
        assertEquals(1, MathHelper.floor(actual.minY));
        assertEquals(1, MathHelper.floor(actual.maxY));
        Box rejected = new Box(0.2, 0, 0.2, 0.8, 1.8, 0.8).expand(width * 0.8f, 1.0E-6, width * 0.8f);
        assertTrue(rejected.minY < 0);
        for (BlockPos position : InsideWallScanGeometry.positions(actual)) assertEquals(1, position.getY());
    }

    @Test
    void orderedPositionsMatchActualVanillaStreamIncludingNegativeAndExactEdges() {
        Box[] boxes = {new Box(-1.1, -1, -0.1, 1, 1, 1), new Box(0, 0, 0, 0, 0, 0),
                InsideWallScanGeometry.eyeBox(new Vec3d(-0.1, 1, -0.1), 0.6f),
                InsideWallScanGeometry.eyeBox(new Vec3d(0.5, 1.62, 0.5), 0.6f)};
        for (Box box : boxes) {
            List<Long> expected = BlockPos.stream(box).map(BlockPos::asLong).toList();
            List<Long> actual = new ArrayList<>();
            for (BlockPos position : InsideWallScanGeometry.positions(box)) actual.add(position.asLong());
            assertEquals(expected, actual);
        }
    }
}
