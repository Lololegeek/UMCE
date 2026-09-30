package io.umce.runtime.collection;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScopedReusableListTest {
    @Test
    void recursiveBorrowCannotOverwriteOuterContentsAndStorageIsReusedAfterClear() {
        AtomicInteger creations = new AtomicInteger();
        ScopedReusableList<Object, ArrayList<Object>> pool = new ScopedReusableList<>(() -> {
            creations.incrementAndGet();
            return new ArrayList<Object>();
        }, ignored -> true);
        ArrayList<Object> first = pool.acquire();
        Object task = new Object();
        first.add(task);
        first.add(task);
        assertNull(pool.acquire());
        assertSame(first, pool.getAcquired());
        assertEquals(2, first.size());
        pool.release();
        assertNull(pool.getAcquired());
        assertTrue(first.isEmpty());
        assertSame(first, pool.acquire());
        assertEquals(1, creations.get());
        pool.release();
    }

    @Test
    void oversizedListsAreClearedAndDiscardedUsingImplementationSpecificCapacityPolicy() {
        ScopedReusableList<Integer, TrackingList> pool = new ScopedReusableList<>(TrackingList::new,
                list -> !list.oversized);
        TrackingList first = pool.acquire();
        for (int index = 0; index < 1000; index++) first.add(index);
        first.oversized = true;
        pool.release();
        assertTrue(first.isEmpty());
        assertNotSame(first, pool.acquire());
        pool.release();
    }

    @Test
    void finallyCleanupAfterCallbackFailureAllowsFreshEmptyBorrow() {
        ScopedReusableList<Object, ArrayList<Object>> pool = new ScopedReusableList<>(ArrayList::new, list -> true);
        assertThrows(IllegalArgumentException.class, () -> {
            ArrayList<Object> list = pool.acquire();
            try {
                list.add(new Object());
                throw new IllegalArgumentException("task failed");
            } finally {
                pool.release();
            }
        });
        assertNull(pool.getAcquired());
        assertTrue(pool.acquire().isEmpty());
        pool.release();
    }

    @Test
    void failingClearAbandonsStorageAndDoesNotLeaveTheLeaseBusy() {
        ScopedReusableList<Integer, TrackingList> pool = new ScopedReusableList<>(TrackingList::new, list -> true);
        TrackingList broken = pool.acquire();
        broken.failClear = true;
        assertThrows(IllegalStateException.class, pool::release);
        assertNull(pool.getAcquired());
        assertNotSame(broken, pool.acquire());
        pool.release();
        assertThrows(IllegalStateException.class, pool::release);
    }

    private static final class TrackingList extends ArrayList<Integer> {
        private boolean oversized;
        private boolean failClear;
        @Override public void clear() {
            if (failClear) throw new IllegalStateException("broken clear");
            super.clear();
        }
    }
}
