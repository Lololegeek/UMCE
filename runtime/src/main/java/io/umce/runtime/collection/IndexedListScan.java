package io.umce.runtime.collection;

import java.util.List;
import java.util.function.Predicate;

/** Ordered, short-circuiting scan for stable, array-backed lists. No result is cached. */
public final class IndexedListScan {
    private IndexedListScan() { }

    public static <T> boolean allMatch(List<T> values, Predicate<? super T> predicate) {
        for (int index = 0, size = values.size(); index < size; index++) {
            if (!predicate.test(values.get(index))) return false;
        }
        return true;
    }
}
