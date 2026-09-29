package io.umce.runtime.collection;

import org.junit.jupiter.api.Test;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IndexedListScanTest {
    @Test
    void preservesOrderAndStopsBeforeReadingLaterSlotsWithoutAllocatingAnIterator() {
        List<Integer> observed = new ArrayList<Integer>();
        List<Integer> slots = new AbstractList<Integer>() {
            @Override public int size() { return 3; }
            @Override public Integer get(int index) {
                if (index == 2) throw new AssertionError("Read beyond first nonmatching slot");
                observed.add(index);
                return index;
            }
            @Override public Iterator<Integer> iterator() { throw new AssertionError("Iterator requested"); }
        };
        assertFalse(IndexedListScan.allMatch(slots, count -> count == 0));
        assertEquals(Arrays.asList(0, 1), observed);
    }

    @Test
    void handlesEmptyListsAndDoesNotReuseResultsAfterInPlaceMutations() {
        assertTrue(IndexedListScan.allMatch(Collections.<Integer>emptyList(), ignored -> {
            throw new AssertionError("Empty list must not inspect slots");
        }));
        List<Integer> counts = Arrays.asList(0, 0, 0);
        assertTrue(IndexedListScan.allMatch(counts, count -> count == 0));
        counts.set(1, 1);
        assertFalse(IndexedListScan.allMatch(counts, count -> count == 0));
        counts.set(1, 0);
        assertTrue(IndexedListScan.allMatch(counts, count -> count == 0));
    }

    @Test
    void keepsCallerExactEqualityRatherThanTreatingOverfullStacksAsFull() {
        assertTrue(IndexedListScan.allMatch(Arrays.asList(64, 64), count -> count == 64));
        assertFalse(IndexedListScan.allMatch(Arrays.asList(64, 65), count -> count == 64));
        assertFalse(IndexedListScan.allMatch(Arrays.asList(64, 0), count -> count == 64));
    }
}
