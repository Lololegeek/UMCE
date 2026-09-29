package io.umce.runtime.collection;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class BoundedSnapshotBufferTest {
    @Test
    void keepsSnapshotOrderDuplicatesAndIdentityAcrossSourceChanges() {
        BoundedSnapshotBuffer<Object> buffer = new BoundedSnapshotBuffer<Object>(128);
        Object first = new Object();
        Object second = new Object();
        Object[] source = {first, second, first};
        assertTrue(buffer.tryAcquire());
        for (Object value : source) buffer.add(value);
        Arrays.fill(source, null);
        assertEquals(Arrays.asList(first, second, first), buffer);
        assertSame(first, buffer.get(0));
        assertSame(first, buffer.get(2));
        buffer.release();
    }

    @Test
    void nestedAcquireDoesNotModifyTheOuterSnapshotAndReleaseClearsReferences() throws Exception {
        BoundedSnapshotBuffer<Object> buffer = new BoundedSnapshotBuffer<Object>(128);
        assertTrue(buffer.tryAcquire());
        Object value = new Object();
        buffer.add(value);
        assertFalse(buffer.tryAcquire());
        assertSame(value, buffer.get(0));
        Field storage = BoundedSnapshotBuffer.class.getDeclaredField("elements");
        storage.setAccessible(true);
        Object[] retained = (Object[]) storage.get(buffer);
        Iterator<Object> staleIterator = buffer.iterator();
        buffer.release();
        assertEquals(0, buffer.size());
        for (Object entry : retained) assertNull(entry);
        assertThrows(java.util.ConcurrentModificationException.class, staleIterator::next);
        assertTrue(buffer.tryAcquire());
        assertTrue(buffer.isEmpty());
        buffer.release();
    }

    @Test
    void exceptionalWorkReleasesAndOversizedSnapshotsDoNotBecomePermanentCaches() {
        BoundedSnapshotBuffer<Integer> buffer = new BoundedSnapshotBuffer<Integer>(16);
        assertThrows(IllegalArgumentException.class, () -> {
            assertTrue(buffer.tryAcquire());
            try {
                for (int index = 0; index < 1000; index++) buffer.add(index);
                throw new IllegalArgumentException("callback failed");
            } finally {
                buffer.release();
            }
        });
        assertEquals(0, buffer.retainedCapacity());
        assertTrue(buffer.tryAcquire());
        buffer.add(7);
        buffer.release();
        assertTrue(buffer.retainedCapacity() <= 16);
    }

    @Test
    void rejectsUnownedWritesAndDoubleRelease() {
        BoundedSnapshotBuffer<Integer> buffer = new BoundedSnapshotBuffer<Integer>(0);
        assertThrows(IllegalStateException.class, () -> buffer.add(1));
        assertThrows(IllegalStateException.class, buffer::release);
        assertTrue(buffer.tryAcquire());
        buffer.add(1);
        buffer.release();
        assertEquals(0, buffer.retainedCapacity());
        assertThrows(IllegalStateException.class, buffer::release);
    }
}
