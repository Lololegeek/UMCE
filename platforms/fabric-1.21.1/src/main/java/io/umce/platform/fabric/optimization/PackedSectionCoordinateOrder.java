package io.umce.platform.fabric.optimization;

/** Iterates signed section coordinates in their unsigned packed-key order. */
public final class PackedSectionCoordinateOrder {
    private PackedSectionCoordinateOrder() { }

    public static int valueAt(int minimum, int maximum, long offset) {
        if (minimum < 0 && maximum >= 0) {
            long nonNegativeCount = (long) maximum + 1L;
            if (offset < nonNegativeCount) return (int) offset;
            return (int) ((long) minimum + offset - nonNegativeCount);
        }
        return (int) ((long) minimum + offset);
    }
}
