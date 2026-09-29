package io.umce.runtime.collection;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

/**
 * Reusable, non-escaping snapshot storage for one owning thread. Active snapshots may grow
 * as needed; oversized storage is discarded on release. Never use as a persistent cache.
 */
public final class BoundedSnapshotBuffer<T> extends AbstractList<T> implements RandomAccess {
    private static final Object[] EMPTY = new Object[0];
    private final int retainedCapacityLimit;
    private Object[] elements = EMPTY;
    private int size;
    private boolean acquired;

    public BoundedSnapshotBuffer(int retainedCapacityLimit) {
        if (retainedCapacityLimit < 0) throw new IllegalArgumentException("Negative retained capacity");
        this.retainedCapacityLimit = retainedCapacityLimit;
    }

    /** A nested operation must use its own vanilla snapshot. */
    public boolean tryAcquire() {
        if (acquired) return false;
        acquired = true;
        return true;
    }

    @Override
    public boolean add(T value) {
        if (!acquired) throw new IllegalStateException("Snapshot is not acquired");
        if (size == elements.length) {
            int nextCapacity = elements.length == 0 ? 8 : elements.length + (elements.length >> 1) + 1;
            if (nextCapacity < 0) throw new OutOfMemoryError("Snapshot capacity overflow");
            elements = Arrays.copyOf(elements, nextCapacity);
        }
        elements[size++] = value;
        modCount++;
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Index: " + index);
        return (T) elements[index];
    }

    @Override public int size() { return size; }

    /** Must be called in finally, even if snapshot construction or a callback fails. */
    public void release() {
        if (!acquired) throw new IllegalStateException("Snapshot is not acquired");
        Arrays.fill(elements, 0, size, null);
        size = 0;
        modCount++;
        acquired = false;
        if (elements.length > retainedCapacityLimit) elements = EMPTY;
    }

    public int retainedCapacity() { return elements.length; }
}
