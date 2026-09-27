package io.umce.platform.fabric.optimization;

import net.minecraft.util.math.ChunkSectionPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackedSectionCoordinateOrderTest {
    private static final int[][] RANGES = {
            {-3, 3}, {-4, -1}, {0, 4}, {-1, 0}, {-2, 2}
    };

    @Test
    void directProbeOrderMatchesVanillaSortedSectionPositions() {
        TreeSet<Long> trackedPositions = new TreeSet<Long>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    if (((x * 31 + y * 17 + z * 13) & 3) != 0) {
                        trackedPositions.add(ChunkSectionPos.asLong(x, y, z));
                    }
                }
            }
        }

        for (int[] xRange : RANGES) {
            for (int[] yRange : RANGES) {
                for (int[] zRange : RANGES) {
                    List<Long> vanilla = vanillaOrder(trackedPositions,
                            xRange[0], xRange[1], yRange[0], yRange[1], zRange[0], zRange[1]);
                    List<Long> direct = directOrder(trackedPositions,
                            xRange[0], xRange[1], yRange[0], yRange[1], zRange[0], zRange[1]);
                    assertEquals(vanilla, direct, "section order for X=" + range(xRange)
                            + " Y=" + range(yRange) + " Z=" + range(zRange));
                }
            }
        }
    }

    private static List<Long> vanillaOrder(TreeSet<Long> tracked, int minX, int maxX,
                                           int minY, int maxY, int minZ, int maxZ) {
        List<Long> result = new ArrayList<Long>();
        for (int x = minX; x <= maxX; x++) {
            long from = ChunkSectionPos.asLong(x, 0, 0);
            long to = ChunkSectionPos.asLong(x, -1, -1) + 1L;
            for (Long packed : tracked.subSet(from, to)) {
                int y = ChunkSectionPos.unpackY(packed.longValue());
                int z = ChunkSectionPos.unpackZ(packed.longValue());
                if (y >= minY && y <= maxY && z >= minZ && z <= maxZ) result.add(packed);
            }
        }
        return result;
    }

    private static List<Long> directOrder(TreeSet<Long> tracked, int minX, int maxX,
                                          int minY, int maxY, int minZ, int maxZ) {
        List<Long> result = new ArrayList<Long>();
        long xCount = (long) maxX - minX + 1L;
        long yCount = (long) maxY - minY + 1L;
        long zCount = (long) maxZ - minZ + 1L;
        for (long xOffset = 0; xOffset < xCount; xOffset++) {
            int x = (int) (minX + xOffset);
            for (long zOffset = 0; zOffset < zCount; zOffset++) {
                int z = PackedSectionCoordinateOrder.valueAt(minZ, maxZ, zOffset);
                for (long yOffset = 0; yOffset < yCount; yOffset++) {
                    int y = PackedSectionCoordinateOrder.valueAt(minY, maxY, yOffset);
                    long packed = ChunkSectionPos.asLong(x, y, z);
                    if (tracked.contains(packed)) result.add(packed);
                }
            }
        }
        return result;
    }

    private static String range(int[] range) {
        return "[" + range[0] + "," + range[1] + "]";
    }
}
