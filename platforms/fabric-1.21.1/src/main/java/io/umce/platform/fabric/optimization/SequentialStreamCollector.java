package io.umce.platform.fabric.optimization;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

/** Sequential Collector terminal path that preserves the stream pipeline and collector contract. */
public final class SequentialStreamCollector {
    private SequentialStreamCollector() { }

    public static <T, A, R> R collect(Stream<T> stream, Collector<T, A, R> collector) {
        if (stream.isParallel()) return stream.collect(collector);

        A container = collector.supplier().get();
        BiConsumer<A, T> accumulator = collector.accumulator();
        Iterator<T> iterator = stream.iterator();
        while (iterator.hasNext()) {
            accumulator.accept(container, iterator.next());
        }
        return collector.finisher().apply(container);
    }
}
