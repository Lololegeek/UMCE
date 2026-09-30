package io.umce.runtime.collection;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One-thread, non-escaping scratch storage using the caller's original list implementation. */
public final class ScopedReusableList<T, L extends List<T>> {
    private final Supplier<L> factory;
    private final Predicate<L> retainStorage;
    private L storage;
    private boolean acquired;

    public ScopedReusableList(Supplier<L> factory, Predicate<L> retainStorage) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.retainStorage = Objects.requireNonNull(retainStorage, "retainStorage");
    }

    public L acquire() {
        if (acquired) return null;
        if (storage == null) storage = Objects.requireNonNull(factory.get(), "created list");
        acquired = true;
        return storage;
    }

    public L getAcquired() { return acquired ? storage : null; }

    public void release() {
        if (!acquired) throw new IllegalStateException("List is not acquired");
        try {
            storage.clear();
            if (!retainStorage.test(storage)) storage = null;
        } catch (RuntimeException | Error failure) {
            storage = null;
            throw failure;
        } finally {
            acquired = false;
        }
    }
}
