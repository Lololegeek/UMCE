package io.umce.platform.fabric.optimization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequentialStreamCollectorTest {
    @Test
    void preservesLimitedSetContentsAndEncounterOrderEffects() {
        List<Integer> observed = new java.util.ArrayList<Integer>();
        Set<Integer> actual = SequentialStreamCollector.collect(
                Stream.of(7, 2, 7, 1, 9, 3).filter(value -> value != 9).limit(4).peek(observed::add),
                Collectors.toSet());

        assertEquals(Set.of(7, 2, 1), actual);
        assertEquals(List.of(7, 2, 7, 1), observed);
    }

    @Test
    void honorsCollectorFinisher() {
        String actual = SequentialStreamCollector.collect(Stream.of("a", "bb", "ccc"),
                Collectors.collectingAndThen(Collectors.toList(), values -> String.join("/", values)));

        assertEquals("a/bb/ccc", actual);
    }

    @Test
    void keepsParallelStreamsOnVanillaCollectorPath() {
        Stream<Integer> stream = Stream.of(1, 2, 3).parallel();

        assertEquals(Set.of(1, 2, 3), SequentialStreamCollector.collect(stream, Collectors.toSet()));
    }
}
