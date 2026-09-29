package io.umce.platform.fabric.optimization;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class BrainTaskListCacheTest {
    @Test
    void flattensOnlyPossibleActivitiesInOriginalIterationOrderAndReusesUntilInvalidated() {
        Map<Integer, Map<String, Set<String>>> taskMap = new LinkedHashMap<Integer, Map<String, Set<String>>>();
        Map<String, Set<String>> lowPriority = new LinkedHashMap<String, Set<String>>();
        lowPriority.put("core", new LinkedHashSet<String>(List.of("core-1", "core-2")));
        lowPriority.put("idle", new LinkedHashSet<String>(List.of("idle-1")));
        Map<String, Set<String>> highPriority = new LinkedHashMap<String, Set<String>>();
        highPriority.put("core", new LinkedHashSet<String>(List.of("core-3")));
        highPriority.put("work", new LinkedHashSet<String>(List.of("work-1", "work-2")));
        taskMap.put(1, lowPriority);
        taskMap.put(0, highPriority);

        BrainTaskListCache<String, String> cache = new BrainTaskListCache<String, String>();
        List<String> active = cache.get(taskMap, Set.of("core", "work"));

        assertEquals(List.of("core-1", "core-2", "core-3", "work-1", "work-2"), active);
        assertSame(active, cache.get(taskMap, Set.of("idle")));
        cache.invalidate();
        List<String> refreshed = cache.get(taskMap, Set.of("idle"));
        assertEquals(List.of("idle-1"), refreshed);
        assertNotSame(active, refreshed);
    }
}
